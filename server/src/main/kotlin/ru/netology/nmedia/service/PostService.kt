package ru.netology.nmedia.service

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.data.repository.findByIdOrNull
import ru.netology.nmedia.dto.Post
import ru.netology.nmedia.entity.PostEntity
import ru.netology.nmedia.exception.NotFoundException
import ru.netology.nmedia.repository.PostRepository
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.CompletableFuture

@Service
@Transactional
class PostService(private val repository: PostRepository) {
    private val client = OkHttpClient()

    fun getAll(): List<Post> = repository
        .findAll(Sort.by(Sort.Direction.DESC, "id"))
        .map { it.toDto() }

    fun getById(id: Long): Post = repository
        .findById(id)
        .map { it.toDto() }
        .orElseThrow { NotFoundException(id) }

    fun save(dto: Post): Post {
        val postEntity = repository.findById(dto.id).orElse(
            PostEntity.fromDto(
                dto.copy(
                    likes = 0,
                    likedByMe = false,
                    published = OffsetDateTime.now().toEpochSecond()
                )
            )
        )

        return if (postEntity.id == 0L) {
            repository.save(postEntity).toDto()
        } else {
            postEntity.content = dto.content
            repository.save(postEntity)
            postEntity.toDto()
        }
    }

    fun removeById(id: Long) {
        repository.findByIdOrNull(id)?.also(repository::delete)
    }

    fun likeById(id: Long): Post {
        val postEntity = repository.findById(id).orElseThrow { NotFoundException(id) }
        postEntity.likes += 1
        postEntity.likedByMe = true
        repository.save(postEntity)
        return postEntity.toDto()
    }

    fun unlikeById(id: Long): Post {
        val postEntity = repository.findById(id).orElseThrow { NotFoundException(id) }
        if (postEntity.likes > 0) {
            postEntity.likes -= 1
        }
        postEntity.likedByMe = false
        repository.save(postEntity)
        return postEntity.toDto()
    }

    fun slowOperation(): CompletableFuture<List<Post>> {
        val future = CompletableFuture<List<Post>>()
        val request = Request.Builder()
            .url("http://localhost:8080/api/posts")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                future.completeExceptionally(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (response.isSuccessful) {

                    val posts = getAll()
                    future.complete(posts)
                } else {
                    future.complete(emptyList())
                }
            }
        })
        return future
    }
}