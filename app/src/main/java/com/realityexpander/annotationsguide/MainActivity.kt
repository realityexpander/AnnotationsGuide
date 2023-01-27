package com.realityexpander.annotationsguide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.realityexpander.annotationsguide.AllowedRegex.Companion.REGEX_DATE
import com.realityexpander.annotationsguide.AllowedRegex.Companion.REGEX_EMAIL
import com.realityexpander.annotationsguide.AllowedRegex.Companion.REGEX_PHONE_NUMBER
import com.realityexpander.annotationsguide.AllowedRegex.Companion.REGEX_ZIP_CODE_PLUS_FOUR
import com.realityexpander.annotationsguide.ui.theme.AnnotationsGuideTheme
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json as KotlinxJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalSerializationApi::class)
    private val api by lazy {
        val contentType = "application/json".toMediaType()
        val json = KotlinxJson {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        }

        val kotlinxSerializer = json.asConverterFactory(contentType)

        Retrofit.Builder()
            .baseUrl("https://jsonplaceholder.typicode.com")
            .client(
                OkHttpClient.Builder()
                    .addInterceptor(AuthInterceptor())
                    .addInterceptor(HttpLoggingInterceptor().setLevel(
                        HttpLoggingInterceptor.Level.BODY)
                    )
                    .build()
            )
//            .addConverterFactory(MoshiConverterFactory.create())
            .addConverterFactory(kotlinxSerializer)
            .build()
            .create(MyApi::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnnotationsGuideTheme {
                Surface(modifier = Modifier.fillMaxSize()) {

                    LaunchedEffect(key1 = true) {
                        launch {
                            val post = api.getPost()

                            // note: new() creates a new instance of User with the same values
                            // but the @AllowedRegex annotations are validated bc GSON/Moshi doesn't call
                            // the `init` block when deserializing.
                            //val user = api.getUser().new()

                            // Using kotlinx.serialization, the `init` block is called when deserializing.
                            val user = api.getUser()

                            println("Post: $post")
                            println("User: $user")
                        }
                    }
                }
            }
        }
    }
}


@Serializable
data class User(
    val name: String,
    val username: String,
    @AllowedRegex(REGEX_EMAIL) val email: String,
    @AllowedRegex(REGEX_DATE) val birthDate: String? = null, // kotlinx.serialization requires default value for optional fields
    val address: Address,
    @AllowedRegex(REGEX_PHONE_NUMBER) val phone: String,
) {
    init {
        validateAllowedRegexFields() // kotlinx.serialization calls the `init` block when deserializing.
    }

    // Must be used when using GSON or Moshi as they don't call the `init` block when deserializing.
    fun new(): User = User(
        name = name,
        username = username,
        email = email,
        birthDate = birthDate,
        address = address.new(),  // NOTE: sub-object must also be copy-created with new() to run validation
        phone = phone,
    )
}

@Serializable
data class Address(
    val street: String,
    val suite: String?,
    val city: String,
    @AllowedRegex(REGEX_ZIP_CODE_PLUS_FOUR) val zipcode: String
) {
    init {
        validateAllowedRegexFields()
    }

    // Must be used when using GSON or Moshi as they don't call the `init` block when deserializing.
    fun new(): Address = Address(
        street = street,
        suite = suite,
        city = city,
        zipcode = zipcode,
    )
}

@Serializable
data class Post(
    val userId: Int,
    val id: Int,
    val title: String,
    val body: String
)

@Target(AnnotationTarget.FIELD)
annotation class AllowedRegex(val regex: String) {
    companion object {
        const val REGEX_DATE = "\\d{4}-\\d{2}-\\d{2}"
        const val REGEX_ZIP_CODE_PLUS_FOUR = "\\d{5}-\\d{4}"
        const val REGEX_EMAIL = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        const val REGEX_PHONE_NUMBER = "\\d{3}-\\d{3}-\\d{4}.*"  // "111-222-3333" //.* allows for optional extension
    }
}
// Validates all fields with @AllowedRegex annotation
fun Any.validateAllowedRegexFields() {
    val declaredFields = this::class.java.declaredFields

    declaredFields.forEach loop1@ { field ->
        field.annotations.forEach loop2@ { annotation ->
            if(field.isAnnotationPresent(AllowedRegex::class.java)) {
                val regex = field.getAnnotation(AllowedRegex::class.java)?.regex
                field.isAccessible = true

                val value = field.get(this) as String?
                value ?: return@loop2

                if(regex?.toRegex()?.matches(value) == false) {
                    throw IllegalArgumentException("Regex does not match: $field = $value, regex = $regex")
                }
            }
        }
    }
}
