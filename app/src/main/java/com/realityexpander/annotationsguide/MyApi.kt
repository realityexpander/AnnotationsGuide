package com.realityexpander.annotationsguide

import retrofit2.http.GET

interface MyApi {

    @GET("/users/2")
    suspend fun getUser() : User

    @GET("/posts/1")
    @Authenticated
    suspend fun getPost() : Post
}

@Target(AnnotationTarget.FUNCTION)
annotation class Authenticated