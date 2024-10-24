package com.example.adminobr.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.TimeUnit
import com.example.adminobr.utils.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.*
import com.example.adminobr.api.ApiService
import com.example.adminobr.data.Usuario
import com.example.adminobr.utils.Constants
import com.example.adminobr.utils.SessionManager
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException

class UsuarioViewModel(application: Application) : AndroidViewModel(application) {

    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Registra el cuerpo de la solicitud y la respuesta
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .readTimeout(30, TimeUnit.SECONDS) // Aumenta el tiempo de espera de lectura
        .build()

    private val sessionManager = SessionManager(application)
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<Event<String>>()
    val error: LiveData<Event<String>> = _error

    private val _mensaje = MutableLiveData<Event<String>>()
    val mensaje: LiveData<Event<String>> = _mensaje

    private val _users = MutableLiveData<List<Usuario>>()
    val users: LiveData<List<Usuario>> = _users

    private val baseUrl = Constants.getBaseUrl()

    private inner class UsuarioApi {
        private val client = OkHttpClient.Builder().build()
        private val guardarUsuarioUrl = Constants.Usuarios.GUARDAR


        suspend fun guardarUsuario(usuario: Usuario): Pair<Boolean, Int?> {
            return withContext(Dispatchers.IO) {
                try{
                    // Obtén empresaDbName de SessionManager
                    val empresaDbName = sessionManager.getEmpresaData()?.db_name ?: return@withContext Pair(false, null)

                    val requestBody = FormBody.Builder()
                        .add("legajo", usuario.legajo)
                        .add("email", usuario.email)
                        .add("dni", usuario.dni)
                        .add("password", usuario.password)
                        .add("nombre", usuario.nombre)
                        .add("apellido", usuario.apellido)
                        .add("telefono", usuario.telefono)
                        .add("userCreated", usuario.userCreated.toString())
                        .add("estadoId", usuario.estadoId.toString())
                        .add("empresaDbName", empresaDbName)  // Agrega empresaDbName al cuerpo de la solicitud
                        .build()

                    val request = Request.Builder()
                        .url("$baseUrl$guardarUsuarioUrl")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    return@withContext handleResponse(response)
                } catch (e: IOException) {
                    Pair(false, null)
                }
            }
        }

        suspend fun actualizarUsuario(usuario: Usuario, newPassword: String?): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val empresaDbName = sessionManager.getEmpresaData()?.db_name ?: return@withContext false
                    Log.d("UsuarioViewModel", "EmpresaDbName: $empresaDbName")

                    val requestBody = FormBody.Builder()
                        .add("id", usuario.id.toString())
                        .add("legajo", usuario.legajo)
                        .add("email", usuario.email)
                        .add("dni", usuario.dni)
                        .add("nombre", usuario.nombre)
                        .add("apellido", usuario.apellido)
                        .add("telefono", usuario.telefono)
                        .add("estado_id", usuario.estadoId.toString()) // Corregido: estado_id
                        .add("empresaDbName", empresaDbName)
                        .apply {
                            if (!newPassword.isNullOrEmpty()) {
                                add("password", newPassword)
                            }
                        }
                        .build()
                    Log.d("UsuarioViewModel", "RequestBody: ${requestBody.toString()}")

                    val request = Request.Builder()
                        //.url("$baseUrl${Constants.Usuarios.ACTUALIZAR}")
                        .url("$baseUrl${Constants.Usuarios.ACTUALIZAR}")
                        .put(requestBody)
                        .build()

                    val response = client.newCall(request).execute()
                    Log.d("UsuarioViewModel", "ResponseCode: ${response.code}") // Log response code

                    // Manejar la respuesta para actualizar (solo necesitamos éxito o fracaso)
                    return@withContext if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        val jsonResponse = responseBody?.let { JSONObject(it) }
                        Log.d("UsuarioViewModel", "ResponseBody: $responseBody") // Log response body
                        val success = jsonResponse?.getBoolean("success") ?: false
                        success
                    } else {
                        false
                    }
                } catch (e: IOException) {
                    Log.e("UsuarioViewModel", "Error en actualizarUsuario: ${e.message}") // Log error
                    false
                }
            }
        }

        private fun handleResponse(response: Response): Pair<Boolean, Int?> {
            Log.d("UsuarioViewModel", "HandleResponse: ${response.code}") // Log response code
            return try {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("UsuarioViewModel", "Respuesta del servidor: ${response.code} - $responseBody")

                    val jsonResponse = responseBody?.let { JSONObject(it) }
                    val success = jsonResponse?.getBoolean("success") ?: false

                    // Verificar si success es false y hay un mensaje de error
                    if (!success && jsonResponse?.has("message") == true) {
                        val errorMessage = jsonResponse.getString("message")
                        _error.postValue(Event(errorMessage)) // Mostrar el mensaje de error en el Toast
                        return Pair(false, null)
                    }

                    // Obtener newId si success es true
                    val newId = if (success) {
                        jsonResponse?.optInt("id")
                    } else {
                        null
                    }

                    Pair(success, newId)
                } else {
                    Log.e("UsuarioViewModel", "Error en la respuesta del servidor: ${response.code}")
                    Pair(false, null)
                }
            } catch (e: JSONException) {
                Log.e("UsuarioViewModel", "Error al parsear la respuesta JSON: ${e.message}")
                Pair(false, null)
            } catch (e: IOException) {
                Log.e("UsuarioViewModel", "Error de conexión: ${e.message}")
                Pair(false, null)
            }
        }

        suspend fun obtenerUsuarios(empresaDbName: String): Response {
            return withContext(Dispatchers.IO) {
                val jsonObject = JSONObject().apply {
                    put("empresaDbName", empresaDbName)
                }
                val requestBody = jsonObject.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("${baseUrl}${Constants.Usuarios.GET_LISTA}") // Elimina el parámetro de la URL
                    .post(requestBody) // Usa POST en lugar de GET
                    .build()
                client.newCall(request).execute()
            }
        }
    }

    private val api = UsuarioApi()

    fun guardarUsuario(usuario: Usuario, callback: (Boolean, Int?) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val (resultado, nuevoId) = api.guardarUsuario(usuario)
                if (resultado) {
                    _mensaje.value = Event("Usuario guardado con éxito")
                    callback(true, nuevoId)
                } else {
                    _error.value = Event("Error al guardar usuario")
                    callback(false, null)
                }
            } catch (e: Exception) {
                _error.value = Event("Error al guardar el usuario: ${e.message}")
                callback(false, null)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun actualizarUsuario(usuario: Usuario, newPassword: String?, callback: (Boolean) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val resultado = api.actualizarUsuario(usuario, newPassword)
                if (resultado) {
                    _mensaje.value = Event("Usuario actualizado con éxito")
                    callback(true)
                } else {
                    _error.value = Event("Error al actualizar el usuario")
                    callback(false)
                }
            } catch (e: Exception) {
                _error.value = Event("Error al actualizar el usuario: ${e.message}")
                callback(false)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadUsers(usuarioFiltro: String = "") {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val empresaDbName = sessionManager.getEmpresaData()?.db_name ?: return@withContext

                    // Crear el JSONObject con los parámetros
                    val jsonObject = JSONObject().apply {
                        put("empresaDbName", empresaDbName)
                        if (usuarioFiltro.isNotEmpty()) {
                            put("usuarioFiltro", usuarioFiltro) // Enviar 'usuarioFiltro' a la API
                        }
                    }

                    val requestBody = jsonObject.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                    val request = Request.Builder()
                        .url("${baseUrl}${Constants.Usuarios.GET_LISTA}")
                        .post(requestBody)
                        .build()

                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val users = parseUsers(response)
                        _users.postValue(users) // Actualizar LiveData sin filtrar
                    } else {
                        _error.postValue(Event("Error al obtener usuarios: ${response.code}"))
                    }
                } catch (e: Exception) {
                    _error.postValue(Event("Error: ${e.message}"))
                }
            }
        }
    }

    // Función para obtener un usuario por su ID
    fun getUsuarioById(userId: Int, callback: (Usuario?) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val empresaDbName = sessionManager.getEmpresaData()?.db_name ?: ""

                    // Crear el RequestBody con los parámetros
                    val jsonObject = JSONObject().apply {
                        put("empresaDbName", empresaDbName)
                        put("id", userId)
                    }
                    val requestBody = jsonObject.toString()
                        .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                    val response = apiService.getUsuarioById(requestBody) // Pasar el RequestBody a la función
                    if (response.isSuccessful) {
                        val usuario = response.body()
                        Log.d("UsuarioViewModel", "Usuario: $usuario")
                        // Ejecutar el callback en el hilo principal
                        withContext(Dispatchers.Main) {
                            callback(usuario)
                        }
                    } else {
                        Log.e("UsuarioViewModel", "Error en la respuesta de la API: ${response.code()} - ${response.message()}")
                        callback(null)
                    }
                } catch (e: Exception) {
                    Log.e("UsuarioViewModel", "Error al obtener el usuario por ID", e)
                    callback(null)
                }
            }
        }
    }

    private fun parseUsers(response: Response): List<Usuario> {
        val userList = mutableListOf<Usuario>()
        try {
            val responseBody = response.body?.string()
            val jsonArray = JSONArray(responseBody)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)

                Log.d("UsuarioViewModel", "JSON object: $jsonObject")
                val usuario = Usuario(
                    id = jsonObject.getString("id").toInt(),
                    legajo = jsonObject.getString("legajo"),
                    email = jsonObject.getString("email"),
                    dni = jsonObject.getString("dni"),
                    nombre = jsonObject.getString("nombre"),
                    apellido = jsonObject.getString("apellido"),
                    telefono = jsonObject.getString("telefono"),
                    estadoId = jsonObject.getInt("estadoId"),
                    userCreated = jsonObject.optInt("userCreated", 0), // Usar optInt() con valor predeterminado 0
                    password = jsonObject.getString("password"),
                    roles = jsonObject.getJSONArray("roles").let { rolesArray ->
                        List(rolesArray.length()) { index ->
                            rolesArray.getString(index)
                        }
                    },
                    principalRole = jsonObject.getString("principalRole"),
                    permisos = jsonObject.getJSONArray("permisos").let { permisosArray ->
                        List(permisosArray.length()) { index ->
                            permisosArray.getString(index)
                        }
                    }
                )
                userList.add(usuario)
            }
        } catch (e: JSONException) {
            Log.e("UsuarioViewModel", "Error al parsear JSON: ${e.message}", e)
        } catch (e: IOException) {
            Log.e("UsuarioViewModel", "Error de E/S: ${e.message}", e)
        }
        return userList
    }

    fun deleteUsuario(usuario: Usuario, empresaDbName: String, callback: (Boolean, Usuario?) -> Unit) = viewModelScope.launch {
        try {
            val response = apiService.deleteUsuario(usuario.id ?: -1, empresaDbName)
            if (response.isSuccessful) {
                // Actualizar la lista de usuarios en el LiveData
                _users.value = _users.value?.filter { it.id != usuario.id }
                _mensaje.value = Event("Usuario eliminado correctamente")
                callback(true, usuario) // Devuelve el usuario eliminado en caso de éxito
            } else {
                _error.value = Event("Error al eliminar el usuario: ${response.message()}")
                callback(false, null) // Devuelve null en caso de error
            }
        } catch (e: Exception) {
            _error.value = Event("Error inesperado: ${e.message}")
            callback(false, null) // Devuelve null en caso de error
        }
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("$baseUrl") // Tu URL base
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }
}

// Factory para el ViewModel
class UsuarioViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UsuarioViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UsuarioViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
