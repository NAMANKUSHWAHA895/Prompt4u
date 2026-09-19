package com.example.data.repository

import com.example.data.local.PostDao
import com.example.data.local.PostEntity
import kotlinx.coroutines.flow.Flow

class PostRepository(private val postDao: PostDao) {
    val allPosts: Flow<List<PostEntity>> = postDao.getAllPosts()
    val favoritePosts: Flow<List<PostEntity>> = postDao.getFavoritePosts()

    fun searchPosts(query: String): Flow<List<PostEntity>> {
        return if (query.isBlank()) {
            postDao.getAllPosts()
        } else {
            postDao.searchPosts(query.trim())
        }
    }

    fun getPostsByCategory(category: String): Flow<List<PostEntity>> {
        return if (category == "All") {
            postDao.getAllPosts()
        } else if (category == "Favorites") {
            postDao.getFavoritePosts()
        } else {
            postDao.getPostsByCategory(category)
        }
    }

    fun getPostsByAudience(audience: String): Flow<List<PostEntity>> {
        return if (audience == "Both" || audience.isBlank()) {
            postDao.getAllPosts()
        } else {
            postDao.getPostsByAudience(audience)
        }
    }

    suspend fun insertPost(post: PostEntity): Long = postDao.insertPost(post)

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        postDao.updateFavoriteStatus(id, isFavorite)
    }

    suspend fun incrementLikes(id: Long) {
        postDao.incrementLikes(id)
    }

    suspend fun deletePost(id: Long) {
        postDao.deletePostById(id)
    }

    suspend fun deleteAllPresetPosts() {
        postDao.deleteAllPresetPosts()
    }
}
