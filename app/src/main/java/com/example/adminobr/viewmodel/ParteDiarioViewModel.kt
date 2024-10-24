package com.example.adminobr.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import androidx.paging.*
import com.example.adminobr.api.ApiService
import com.example.adminobr.data.ListarPartesDiarios
import com.example.adminobr.data.ParteDiario
import com.example.adminobr.utils.Event
import com.example.adminobr.ui.partediario.ParteDiarioPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

import com.example.adminobr.utils.Constants
import com.example.adminobr.utils.SessionManager
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ParteDiarioViewModel(application: Application) : AndroidViewModel(application) {

    //private val client = OkHttpClient.Builder().build()

    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // Registra el cuerpo de la solicitud y la respuesta
    }

    val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val BASE_URL = Constants.getBaseUrl()
    private val GUARDAR_PARTE_DIARIO = Constants.PartesDiarios.GUARDAR_PARTE_DIARIO
    private val sessionManager = SessionManager(application)

    private val _partesList = MutableLiveData<List<ListarPartesDiarios>>()
    val partesList: LiveData<List<ListarPartesDiarios>> = _partesList

    private val _mensaje = MutableLiveData<Event<String?>>()
    val mensaje: LiveData<Event<String?>> = _mensaje

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<Event<String?>>()
    val error: LiveData<Event<String?>> = _error

    private val _filterEquipo = MutableLiveData<String>()
    private val _filterFechaInicio = MutableLiveData<String>()
    private val _filterFechaFin = MutableLiveData<String>()

    val partesDiarios: Flow<PagingData<ListarPartesDiarios>> = combine(
        _filterEquipo.asFlow(),
        _filterFechaInicio.asFlow(),
        _filterFechaFin.asFlow()
    ) { equipo, fechaInicio, fechaFin ->
        Triple(equipo, fechaInicio, fechaFin)
    }.flatMapLatest { (equipo, fechaInicio, fechaFin) ->
        Log.d("ParteDiarioViewModel", "Fetching data with filter - Equipo: $equipo, FechaInicio: $fechaInicio, FechaFin: $fechaFin")
        val empresaDbName = sessionManager.getEmpresaData()?.db_name ?: ""
        Pager(PagingConfig(pageSize = 20)) {
            ParteDiarioPagingSource(client,
                BASE_URL.toString(), equipo ?: "", fechaInicio ?: "", fechaFin ?: "", empresaDbName)
        }.flow.cachedIn(viewModelScope)
    }

    private inner class NuevoParteDiarioApi {

        suspend fun guardarParteDiario(parteDiario: ParteDiario): Pair<Boolean, Int?> {
            return withContext(Dispatchers.IO) {
                try{
                    // Obtén empresaDbName de SessionManager
                    val empresaDbName = sessionManager.getEmpresaData()?.db_name ?: return@withContext Pair(false, null)
//                    val empresaDbName = sessionManager.getEmpresaDbName()
                    Log.d("ParteDiarioViewModel", "empresaDbName: $empresaDbName")

                    val requestBody = FormBody.Builder()
                        .add("fecha", parteDiario.fecha)
                        .add("equipoId", parteDiario.equipoId.toString())
                        .add("horasInicio", parteDiario.horasInicio.toString())
                        .add("horasFin", parteDiario.horasFin.toString())
                        .add("horasTrabajadas", parteDiario.horasTrabajadas.toString())
                        .add("observaciones", parteDiario.observaciones ?: "")
                        .add("obraId", parteDiario.obraId.toString())
                        .add("userCreated", parteDiario.userCreated.toString())
                        .add("estadoId", parteDiario.estadoId.toString())

                        .add("combustible_tipo", parteDiario.combustible_tipo ?: "")
                        .add("combustible_cant", parteDiario.combustible_cant.toString())
                        .add("aceite_motor_cant", parteDiario.aceite_motor_cant.toString())
                        .add("aceite_hidra_cant", parteDiario.aceite_hidra_cant.toString())
                        .add("aceite_otro_cant", parteDiario.aceite_otro_cant.toString())
                        .add("engrase_general", parteDiario.engrase_general.toString())
                        .add("filtro_aire", parteDiario.filtro_aire.toString())
                        .add("filtro_aceite", parteDiario.filtro_aceite.toString())
                        .add("filtro_comb", parteDiario.filtro_comb.toString())
                        .add("filtro_otro", parteDiario.filtro_otro.toString())

                        .add("engrase_general", if (parteDiario.engrase_general == true) "1" else "0")
                        .add("filtro_aire", if (parteDiario.filtro_aire == true) "1" else "0")
                        .add("filtro_aceite", if (parteDiario.filtro_aceite == true) "1" else "0")
                        .add("filtro_comb", if (parteDiario.filtro_comb == true) "1" else "0")
                        .add("filtro_otro", if (parteDiario.filtro_otro == true) "1" else "0")

                        .add("empresaDbName", empresaDbName)  // Agrega empresaDbName al cuerpo de la solicitud
                        .build()

                    val request = Request.Builder()
                        .url("$BASE_URL$GUARDAR_PARTE_DIARIO")
                        .post(requestBody)
                        .build()

                    Log.d("ParteDiarioPagingSource", "Request URL: \"$BASE_URL${GUARDAR_PARTE_DIARIO}\"")

                    Log.d("ParteDiarioViewModel", "Enviando datos al servidor: $requestBody")

                    val response = client.newCall(request).execute()
                    return@withContext handleResponse(response)
                } catch (e: IOException) {
                    Pair(false, null)
                }
            }
        }

        private fun handleResponse(response: Response): Pair<Boolean, Int?> {
            val responseBody = response.body?.string()
            Log.d("ParteDiarioViewModel", "Respuesta del servidor: ${response.code} - $responseBody")

            return if (response.isSuccessful) {
                val jsonResponse = responseBody?.let { JSONObject(it) }
                val success = jsonResponse?.getBoolean("success") ?: false
                val newId = jsonResponse?.optInt("id")
                Pair(success, newId)
            } else {
                Log.e("ParteDiarioViewModel", "Error en la respuesta del servidor: ${response.code} - $responseBody")
                Pair(false, null)
            }
        }
    }

    private val api = NuevoParteDiarioApi()

    fun setFilter(equipo: String, fechaInicio: String, fechaFin: String) {
        Log.d("ParteDiarioViewModel", "Setting filter - Equipo: $equipo, FechaInicio: $fechaInicio, FechaFin: $fechaFin")
        _filterEquipo.value = equipo
        _filterFechaInicio.value = fechaInicio
        _filterFechaFin.value = fechaFin
    }

    fun getUltimosPartesDiarios(userId: Int): LiveData<List<ListarPartesDiarios>> {
        val liveData = MutableLiveData<List<ListarPartesDiarios>>() // Corregir el tipo de liveData
        viewModelScope.launch {
            try {
                val empresaDbName = sessionManager.getEmpresaData()?.db_name ?: ""

                // Crear el objeto JSON para la solicitud
                val jsonBody = JSONObject().apply {
                    put("empresaDbName", empresaDbName)
                    put("limit", 5)
                    put("user_created", userId)
                    // Agregar otros parámetros opcionales si es necesario
                }

                // Define el MediaType para JSON
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val requestBody = jsonBody.toString().toRequestBody(mediaType)

                // Realizar la solicitud POST
                val response = apiService.getUltimosPartesDiarios(requestBody) // Pasar el requestBody

                if (response.isSuccessful) {
                    liveData.value = response.body()
                } else {
                    _error.value = Event("Error al obtener los últimos partes diarios: ${response.message()}")
                }
            } catch (e: Exception) {
                _error.value = Event("Error inesperado: ${e.message}")
            }
        }
        return liveData
    }

    fun guardarParteDiario(parteDiario: ParteDiario, callback: (Boolean, Int?) -> Unit) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                //val fechaConvertida = convertirFecha(parteDiario.fecha)
                //val parteDiarioConvertido = parteDiario.copy(fecha = fechaConvertida)
//                val (resultado, nuevoId) = withContext(Dispatchers.IO) {
//                    guardarParteDiarioEnBaseDeDatos(parteDiarioConvertido)
//                }

                val (resultado, nuevoId) = api.guardarParteDiario(parteDiario)
                if (resultado) {
                    _mensaje.value = Event("Parte diario guardado con éxito")
                    callback(true, nuevoId)
                } else {
                    _error.value = Event("Error al guardar el parte diario")
                    callback(false, null)
                }
            } catch (e: Exception) {
                _error.value = Event("Error al guardar el parte diario: ${e.message}")
                callback(false, null)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun convertirFecha(fechaOriginal: String): String {
        val parts = fechaOriginal.split("/")
        return if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            fechaOriginal
        }
    }

    fun clearPartesList() {
        _partesList.value = emptyList()
    }

    fun updateParteDiario(parteDiario: ListarPartesDiarios, empresaDbName: String) = viewModelScope.launch {
        try {
            val response = apiService.updateParteDiario(parteDiario, empresaDbName)
            if (response.isSuccessful) {
                // Actualizar la lista de partes diarios (opcional, si quieres una actualización inmediata)
                // ...
                _mensaje.value = Event("Parte diario actualizado correctamente")
            } else {
                _error.value = Event("Error al actualizar el parte diario: ${response.message()}")
            }
        } catch (e: Exception) {
            _error.value = Event("Error inesperado: ${e.message}")
        }
    }

//    fun deleteParteDiario(idParteDiario: Int, empresaDbName: String, callback: (Boolean) -> Unit) = viewModelScope.launch {
//        try {
//            Log.d("ParteDiarioViewModel", "Eliminando parte diario con ID: $idParteDiario")
//            val response = apiService.deleteParteDiario(idParteDiario, empresaDbName)
//            Log.d("ParteDiarioViewModel", "Código de respuesta de la API: ${response.code()}")
//            if (response.isSuccessful) {
//                // Invalida la fuente de datos para refrescar la lista
//                //pager.value?.refresh()
//                _mensaje.value = Event("Parte diario eliminado correctamente")
//                callback(true)
//                Log.d("ParteDiarioViewModel", "Parte diario eliminado correctamente") // Log de éxito
//            } else {
//                _error.value = Event("Error al eliminar el parte diario: ${response.message()}") // Log de error con mensaje
//                callback(false)
//            }
//        } catch (e: Exception) {
//            _error.value = Event("Error inesperado: ${e.message}")
//            callback(false)
//        }
//    }
    fun deleteParteDiario(parteDiario: ListarPartesDiarios, empresaDbName: String, callback: (Boolean, ListarPartesDiarios?) -> Unit) = viewModelScope.launch {
        try {
            Log.d("ParteDiarioViewModel", "Eliminando parte diario con ID: ${parteDiario.id_parte_diario}")
            val response = apiService.deleteParteDiario(parteDiario.id_parte_diario, empresaDbName)
            Log.d("ParteDiarioViewModel", "Código de respuesta de la API: ${response.code()}")
            if (response.isSuccessful) {
                _mensaje.value = Event("Parte diario eliminado correctamente")
                callback(true, parteDiario) // Devuelve el parte eliminado en caso de éxito
                Log.d("ParteDiarioViewModel", "Parte diario eliminado correctamente")
            } else {
                _error.value = Event("Error al eliminar el parte diario: ${response.message()}")
                callback(false, null) // Devuelve null en caso de error
            }
        } catch (e: Exception) {
            _error.value = Event("Error inesperado: ${e.message}")
            callback(false, null) // Devuelve null en caso de error
        }
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("$BASE_URL") // Tu URL base
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(ApiService::class.java)
    }

}