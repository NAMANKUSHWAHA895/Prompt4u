package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun getAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoritePosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE category = :category ORDER BY createdAt DESC")
    fun getPostsByCategory(category: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE audience = :audience OR audience = 'Both' ORDER BY createdAt DESC")
    fun getPostsByAudience(audience: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE prompt LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchPosts(query: String): Flow<List<PostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(posts: List<PostEntity>)

    @Update
    suspend fun updatePost(post: PostEntity)

    @Query("UPDATE posts SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("UPDATE posts SET likesCount = likesCount + 1 WHERE id = :id")
    suspend fun incrementLikes(id: Long)

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deletePostById(id: Long)

    @Query("DELETE FROM posts WHERE imageUrl LIKE 'preset_%'")
    suspend fun deleteAllPresetPosts()

    @Query("DELETE FROM posts")
    suspend fun clearAllPosts()

    @Query("SELECT COUNT(*) FROM posts")
    suspend fun getPostCount(): Int
}
