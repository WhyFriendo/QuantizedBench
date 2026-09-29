//package ai.mlc.mlcchat
//
//import ai.mlc.mlcllm.MLCEngine
//import ai.mlc.mlcllm.OpenAIProtocol
//import android.app.Application
//import android.content.ClipData
//import android.content.ClipboardManager
//import android.content.Context
//import android.os.Environment
//import android.widget.Toast
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.toMutableStateList
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.viewModelScope
//import com.google.gson.Gson
//import com.google.gson.annotations.SerializedName
//import kotlinx.coroutines.launch
//import java.io.File
//import java.io.FileOutputStream
//import java.net.URL
//import java.nio.channels.Channels
//import java.util.UUID
//import java.util.concurrent.Executors
//import kotlin.concurrent.thread
//import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
//import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessageContent
//import android.app.Activity
//import kotlinx.coroutines.*
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.net.Uri
//import java.io.ByteArrayOutputStream
//import android.util.Base64
//import android.util.Log
//
//class AppViewModel(application: Application) : AndroidViewModel(application) {
//    val modelList = emptyList<ModelState>().toMutableStateList()
//    val chatState = ChatState()
//    val modelSampleList = emptyList<ModelRecord>().toMutableStateList()
//    private var showAlert = mutableStateOf(false)
//    private var alertMessage = mutableStateOf("")
//    private var appConfig = AppConfig(
//        emptyList<String>().toMutableList(),
//        emptyList<ModelRecord>().toMutableList()
//    )
//    private val application = getApplication<Application>()
//    private val appDirFile = application.getExternalFilesDir("")
//    private val gson = Gson()
//    private val modelIdSet = emptySet<String>().toMutableSet()
//
//    companion object {
//        const val AppConfigFilename = "mlc-app-config.json"
//        const val ModelConfigFilename = "mlc-chat-config.json"
//        const val ParamsConfigFilename = "tensor-cache.json"
//        const val ModelUrlSuffix = "resolve/main/"
//    }
//
//    init {
//        loadAppConfig()
//    }
//
//    fun isShowingAlert(): Boolean {
//        return showAlert.value
//    }
//
//    fun errorMessage(): String {
//        return alertMessage.value
//    }
//
//    fun dismissAlert() {
//        require(showAlert.value)
//        showAlert.value = false
//    }
//
//    fun copyError() {
//        require(showAlert.value)
//        val clipboard =
//            application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//        clipboard.setPrimaryClip(ClipData.newPlainText("MLCChat", errorMessage()))
//    }
//
//    private fun issueAlert(error: String) {
//        showAlert.value = true
//        alertMessage.value = error
//    }
//
//    fun requestDeleteModel(modelId: String) {
//        deleteModel(modelId)
//        issueAlert("Model: $modelId has been deleted")
//    }
//
//
//    private fun loadAppConfig() {
//        val appConfigFile = File(appDirFile, AppConfigFilename)
//        val jsonString: String = if (!appConfigFile.exists()) {
//            application.assets.open(AppConfigFilename).bufferedReader().use { it.readText() }
//        } else {
//            appConfigFile.readText()
//        }
//        appConfig = gson.fromJson(jsonString, AppConfig::class.java)
//        appConfig.modelLibs = emptyList<String>().toMutableList()
//        modelList.clear()
//        modelIdSet.clear()
//        modelSampleList.clear()
//        for (modelRecord in appConfig.modelList) {
//            appConfig.modelLibs.add(modelRecord.modelLib)
//            val modelDirFile = File(appDirFile, modelRecord.modelId)
//            val modelConfigFile = File(modelDirFile, ModelConfigFilename)
//            if (modelConfigFile.exists()) {
//                val modelConfigString = modelConfigFile.readText()
//                val modelConfig = gson.fromJson(modelConfigString, ModelConfig::class.java)
//                modelConfig.modelId = modelRecord.modelId
//                modelConfig.modelLib = modelRecord.modelLib
//                modelConfig.estimatedVramBytes = modelRecord.estimatedVramBytes
//                addModelConfig(modelConfig, modelRecord.modelUrl, true)
//            } else {
//                downloadModelConfig(
//                    if (modelRecord.modelUrl.endsWith("/")) modelRecord.modelUrl else "${modelRecord.modelUrl}/",
//                    modelRecord,
//                    true
//                )
//            }
//        }
//    }
//
//    private fun updateAppConfig(action: () -> Unit) {
//        action()
//        val jsonString = gson.toJson(appConfig)
//        val appConfigFile = File(appDirFile, AppConfigFilename)
//        appConfigFile.writeText(jsonString)
//    }
//
//    private fun addModelConfig(modelConfig: ModelConfig, modelUrl: String, isBuiltin: Boolean) {
//        require(!modelIdSet.contains(modelConfig.modelId))
//        modelIdSet.add(modelConfig.modelId)
//        modelList.add(
//            ModelState(
//                modelConfig,
//                modelUrl + if (modelUrl.endsWith("/")) "" else "/",
//                File(appDirFile, modelConfig.modelId)
//            )
//        )
//        if (!isBuiltin) {
//            updateAppConfig {
//                appConfig.modelList.add(
//                    ModelRecord(
//                        modelUrl,
//                        modelConfig.modelId,
//                        modelConfig.estimatedVramBytes,
//                        modelConfig.modelLib
//                    )
//                )
//            }
//        }
//    }
//
//    private fun deleteModel(modelId: String) {
//        val modelDirFile = File(appDirFile, modelId)
//        modelDirFile.deleteRecursively()
//        require(!modelDirFile.exists())
//        modelIdSet.remove(modelId)
//        modelList.removeIf { modelState -> modelState.modelConfig.modelId == modelId }
//        updateAppConfig {
//            appConfig.modelList.removeIf { modelRecord -> modelRecord.modelId == modelId }
//        }
//    }
//
//    private fun isModelConfigAllowed(modelConfig: ModelConfig): Boolean {
//        if (appConfig.modelLibs.contains(modelConfig.modelLib)) return true
//        viewModelScope.launch {
//            issueAlert("Model lib ${modelConfig.modelLib} is not supported.")
//        }
//        return false
//    }
//
//
//    private fun downloadModelConfig(
//        modelUrl: String,
//        modelRecord: ModelRecord,
//        isBuiltin: Boolean
//    ) {
//        thread(start = true) {
//            try {
//                val url = URL("${modelUrl}${ModelUrlSuffix}${ModelConfigFilename}")
//                val tempId = UUID.randomUUID().toString()
//                val tempFile = File(
//                    application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
//                    tempId
//                )
//                url.openStream().use {
//                    Channels.newChannel(it).use { src ->
//                        FileOutputStream(tempFile).use { fileOutputStream ->
//                            fileOutputStream.channel.transferFrom(src, 0, Long.MAX_VALUE)
//                        }
//                    }
//                }
//                require(tempFile.exists())
//                viewModelScope.launch {
//                    try {
//                        val modelConfigString = tempFile.readText()
//                        val modelConfig = gson.fromJson(modelConfigString, ModelConfig::class.java)
//                        modelConfig.modelId = modelRecord.modelId
//                        modelConfig.modelLib = modelRecord.modelLib
//                        modelConfig.estimatedVramBytes = modelRecord.estimatedVramBytes
//                        if (modelIdSet.contains(modelConfig.modelId)) {
//                            tempFile.delete()
//                            issueAlert("${modelConfig.modelId} has been used, please consider another local ID")
//                            return@launch
//                        }
//                        if (!isModelConfigAllowed(modelConfig)) {
//                            tempFile.delete()
//                            return@launch
//                        }
//                        val modelDirFile = File(appDirFile, modelConfig.modelId)
//                        val modelConfigFile = File(modelDirFile, ModelConfigFilename)
//                        tempFile.copyTo(modelConfigFile, overwrite = true)
//                        tempFile.delete()
//                        require(modelConfigFile.exists())
//                        addModelConfig(modelConfig, modelUrl, isBuiltin)
//                    } catch (e: Exception) {
//                        viewModelScope.launch {
//                            issueAlert("Add model failed: ${e.localizedMessage}")
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                viewModelScope.launch {
//                    issueAlert("Download model config failed: ${e.localizedMessage}")
//                }
//            }
//
//        }
//    }
//
//    inner class ModelState(
//        val modelConfig: ModelConfig,
//        private val modelUrl: String,
//        private val modelDirFile: File
//    ) {
//        var modelInitState = mutableStateOf(ModelInitState.Initializing)
//        private var paramsConfig = ParamsConfig(emptyList())
//        val progress = mutableStateOf(0)
//        val total = mutableStateOf(1)
//        val id: UUID = UUID.randomUUID()
//        private val remainingTasks = emptySet<DownloadTask>().toMutableSet()
//        private val downloadingTasks = emptySet<DownloadTask>().toMutableSet()
//        private val maxDownloadTasks = 3
//        private val gson = Gson()
//
//
//        init {
//            switchToInitializing()
//        }
//
//        private fun switchToInitializing() {
//            val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
//            if (paramsConfigFile.exists()) {
//                loadParamsConfig()
//                switchToIndexing()
//            } else {
//                downloadParamsConfig()
//            }
//        }
//
//        private fun loadParamsConfig() {
//            val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
//            require(paramsConfigFile.exists())
//            val jsonString = paramsConfigFile.readText()
//            paramsConfig = gson.fromJson(jsonString, ParamsConfig::class.java)
//        }
//
//        private fun downloadParamsConfig() {
//            thread(start = true) {
//                val url = URL("${modelUrl}${ModelUrlSuffix}${ParamsConfigFilename}")
//                val tempId = UUID.randomUUID().toString()
//                val tempFile = File(modelDirFile, tempId)
//                url.openStream().use {
//                    Channels.newChannel(it).use { src ->
//                        FileOutputStream(tempFile).use { fileOutputStream ->
//                            fileOutputStream.channel.transferFrom(src, 0, Long.MAX_VALUE)
//                        }
//                    }
//                }
//                require(tempFile.exists())
//                val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
//                tempFile.renameTo(paramsConfigFile)
//                require(paramsConfigFile.exists())
//                viewModelScope.launch {
//                    loadParamsConfig()
//                    switchToIndexing()
//                }
//            }
//        }
//
//        fun handleStart() {
//            switchToDownloading()
//        }
//
//        fun handlePause() {
//            switchToPausing()
//        }
//
//        fun handleClear() {
//            require(
//                modelInitState.value == ModelInitState.Downloading ||
//                        modelInitState.value == ModelInitState.Paused ||
//                        modelInitState.value == ModelInitState.Finished
//            )
//            switchToClearing()
//        }
//
//        private fun switchToClearing() {
//            if (modelInitState.value == ModelInitState.Paused) {
//                modelInitState.value = ModelInitState.Clearing
//                clear()
//            } else if (modelInitState.value == ModelInitState.Finished) {
//                modelInitState.value = ModelInitState.Clearing
//                if (chatState.modelName.value == modelConfig.modelId) {
//                    chatState.requestTerminateChat { clear() }
//                } else {
//                    clear()
//                }
//            } else {
//                modelInitState.value = ModelInitState.Clearing
//            }
//        }
//
//        fun handleDelete() {
//            require(
//                modelInitState.value == ModelInitState.Downloading ||
//                        modelInitState.value == ModelInitState.Paused ||
//                        modelInitState.value == ModelInitState.Finished
//            )
//            switchToDeleting()
//        }
//
//        private fun switchToDeleting() {
//            if (modelInitState.value == ModelInitState.Paused) {
//                modelInitState.value = ModelInitState.Deleting
//                delete()
//            } else if (modelInitState.value == ModelInitState.Finished) {
//                modelInitState.value = ModelInitState.Deleting
//                if (chatState.modelName.value == modelConfig.modelId) {
//                    chatState.requestTerminateChat { delete() }
//                } else {
//                    delete()
//                }
//            } else {
//                modelInitState.value = ModelInitState.Deleting
//            }
//        }
//
//        private fun switchToIndexing() {
//            modelInitState.value = ModelInitState.Indexing
//            progress.value = 0
//            total.value = modelConfig.tokenizerFiles.size + paramsConfig.paramsRecords.size
//            for (tokenizerFilename in modelConfig.tokenizerFiles) {
//                val file = File(modelDirFile, tokenizerFilename)
//                if (file.exists()) {
//                    ++progress.value
//                } else {
//                    remainingTasks.add(
//                        DownloadTask(
//                            URL("${modelUrl}${ModelUrlSuffix}${tokenizerFilename}"),
//                            file
//                        )
//                    )
//                }
//            }
//            for (paramsRecord in paramsConfig.paramsRecords) {
//                val file = File(modelDirFile, paramsRecord.dataPath)
//                if (file.exists()) {
//                    ++progress.value
//                } else {
//                    remainingTasks.add(
//                        DownloadTask(
//                            URL("${modelUrl}${ModelUrlSuffix}${paramsRecord.dataPath}"),
//                            file
//                        )
//                    )
//                }
//            }
//            if (progress.value < total.value) {
//                switchToPaused()
//            } else {
//                switchToFinished()
//            }
//        }
//
//        private fun switchToDownloading() {
//            modelInitState.value = ModelInitState.Downloading
//            for (downloadTask in remainingTasks) {
//                if (downloadingTasks.size < maxDownloadTasks) {
//                    handleNewDownload(downloadTask)
//                } else {
//                    return
//                }
//            }
//        }
//
//        private fun handleNewDownload(downloadTask: DownloadTask) {
//            require(modelInitState.value == ModelInitState.Downloading)
//            require(!downloadingTasks.contains(downloadTask))
//            downloadingTasks.add(downloadTask)
//            thread(start = true) {
//                val tempId = UUID.randomUUID().toString()
//                val tempFile = File(modelDirFile, tempId)
//                downloadTask.url.openStream().use {
//                    Channels.newChannel(it).use { src ->
//                        FileOutputStream(tempFile).use { fileOutputStream ->
//                            fileOutputStream.channel.transferFrom(src, 0, Long.MAX_VALUE)
//                        }
//                    }
//                }
//                require(tempFile.exists())
//                tempFile.renameTo(downloadTask.file)
//                require(downloadTask.file.exists())
//                viewModelScope.launch {
//                    handleFinishDownload(downloadTask)
//                }
//            }
//        }
//
//        private fun handleNextDownload() {
//            require(modelInitState.value == ModelInitState.Downloading)
//            for (downloadTask in remainingTasks) {
//                if (!downloadingTasks.contains(downloadTask)) {
//                    handleNewDownload(downloadTask)
//                    break
//                }
//            }
//        }
//
//        private fun handleFinishDownload(downloadTask: DownloadTask) {
//            remainingTasks.remove(downloadTask)
//            downloadingTasks.remove(downloadTask)
//            ++progress.value
//            require(
//                modelInitState.value == ModelInitState.Downloading ||
//                        modelInitState.value == ModelInitState.Pausing ||
//                        modelInitState.value == ModelInitState.Clearing ||
//                        modelInitState.value == ModelInitState.Deleting
//            )
//            if (modelInitState.value == ModelInitState.Downloading) {
//                if (remainingTasks.isEmpty()) {
//                    if (downloadingTasks.isEmpty()) {
//                        switchToFinished()
//                    }
//                } else {
//                    handleNextDownload()
//                }
//            } else if (modelInitState.value == ModelInitState.Pausing) {
//                if (downloadingTasks.isEmpty()) {
//                    switchToPaused()
//                }
//            } else if (modelInitState.value == ModelInitState.Clearing) {
//                if (downloadingTasks.isEmpty()) {
//                    clear()
//                }
//            } else if (modelInitState.value == ModelInitState.Deleting) {
//                if (downloadingTasks.isEmpty()) {
//                    delete()
//                }
//            }
//        }
//
//        private fun clear() {
//            val files = modelDirFile.listFiles { dir, name ->
//                !(dir == modelDirFile && name == ModelConfigFilename)
//            }
//            require(files != null)
//            for (file in files) {
//                file.deleteRecursively()
//                require(!file.exists())
//            }
//            val modelConfigFile = File(modelDirFile, ModelConfigFilename)
//            require(modelConfigFile.exists())
//            switchToIndexing()
//        }
//
//        private fun delete() {
//            modelDirFile.deleteRecursively()
//            require(!modelDirFile.exists())
//            requestDeleteModel(modelConfig.modelId)
//        }
//
//        private fun switchToPausing() {
//            modelInitState.value = ModelInitState.Pausing
//        }
//
//        private fun switchToPaused() {
//            modelInitState.value = ModelInitState.Paused
//        }
//
//
//        private fun switchToFinished() {
//            modelInitState.value = ModelInitState.Finished
//        }
//
//        fun startChat() {
//            chatState.requestReloadChat(
//                modelConfig,
//                modelDirFile.absolutePath,
//            )
//        }
//
//    }
//
//    inner class ChatState {
//        val messages = emptyList<MessageData>().toMutableStateList()
//        val report = mutableStateOf("")
//        val modelName = mutableStateOf("")
//        private var modelChatState = mutableStateOf(ModelChatState.Ready)
//            @Synchronized get
//            @Synchronized set
//        private val engine = MLCEngine()
//        private var historyMessages = mutableListOf<ChatCompletionMessage>()
//        private var modelLib = ""
//        private var modelPath = ""
//        private val executorService = Executors.newSingleThreadExecutor()
//        private val viewModelScope = CoroutineScope(Dispatchers.Main + Job())
//        private var imageUri: Uri? = null
//        private fun mainResetChat() {
//            imageUri = null
//            executorService.submit {
//                callBackend { engine.reset() }
//                historyMessages = mutableListOf<ChatCompletionMessage>()
//                viewModelScope.launch {
//                    clearHistory()
//                    switchToReady()
//                }
//            }
//        }
//
//        private fun clearHistory() {
//            messages.clear()
//            report.value = ""
//            historyMessages.clear()
//        }
//
//
//        private fun switchToResetting() {
//            modelChatState.value = ModelChatState.Resetting
//        }
//
//        private fun switchToGenerating() {
//            modelChatState.value = ModelChatState.Generating
//        }
//
//        private fun switchToReloading() {
//            modelChatState.value = ModelChatState.Reloading
//        }
//
//        private fun switchToReady() {
//            modelChatState.value = ModelChatState.Ready
//        }
//
//        private fun switchToFailed() {
//            modelChatState.value = ModelChatState.Falied
//        }
//
//        private fun callBackend(callback: () -> Unit): Boolean {
//            try {
//                callback()
//            } catch (e: Exception) {
//                viewModelScope.launch {
//                    val stackTrace = e.stackTraceToString()
//                    val errorMessage = e.localizedMessage
//                    appendMessage(
//                        MessageRole.Assistant,
//                        "MLCChat failed\n\nStack trace:\n$stackTrace\n\nError message:\n$errorMessage"
//                    )
//                    switchToFailed()
//                }
//                return false
//            }
//            return true
//        }
//
//        fun requestResetChat() {
//            require(interruptable())
//            interruptChat(
//                prologue = {
//                    switchToResetting()
//                },
//                epilogue = {
//                    mainResetChat()
//                }
//            )
//        }
//
//        private fun interruptChat(prologue: () -> Unit, epilogue: () -> Unit) {
//            // prologue runs before interruption
//            // epilogue runs after interruption
//            require(interruptable())
//            if (modelChatState.value == ModelChatState.Ready) {
//                prologue()
//                epilogue()
//            } else if (modelChatState.value == ModelChatState.Generating) {
//                prologue()
//                executorService.submit {
//                    viewModelScope.launch { epilogue() }
//                }
//            } else {
//                require(false)
//            }
//        }
//
//        fun requestTerminateChat(callback: () -> Unit) {
//            require(interruptable())
//            interruptChat(
//                prologue = {
//                    switchToTerminating()
//                },
//                epilogue = {
//                    mainTerminateChat(callback)
//                }
//            )
//        }
//
//        private fun mainTerminateChat(callback: () -> Unit) {
//            executorService.submit {
//                callBackend { engine.unload() }
//                viewModelScope.launch {
//                    clearHistory()
//                    switchToReady()
//                    callback()
//                }
//            }
//        }
//
//        private fun switchToTerminating() {
//            modelChatState.value = ModelChatState.Terminating
//        }
//
//
//        fun requestReloadChat(modelConfig: ModelConfig, modelPath: String) {
//
//            if (this.modelName.value == modelConfig.modelId && this.modelLib == modelConfig.modelLib && this.modelPath == modelPath) {
//                return
//            }
//            require(interruptable())
//            interruptChat(
//                prologue = {
//                    switchToReloading()
//                },
//                epilogue = {
//                    mainReloadChat(modelConfig, modelPath)
//                }
//            )
//        }
//
//        private fun mainReloadChat(modelConfig: ModelConfig, modelPath: String) {
//            clearHistory()
//            this.modelName.value = modelConfig.modelId
//            this.modelLib = modelConfig.modelLib
//            this.modelPath = modelPath
//            executorService.submit {
//                viewModelScope.launch {
//                    Toast.makeText(application, "Initialize...", Toast.LENGTH_SHORT).show()
//                }
//                if (!callBackend {
//                        engine.unload()
//                        engine.reload(modelPath, modelConfig.modelLib)
//                    }) return@submit
//                viewModelScope.launch {
//                    Toast.makeText(application, "Ready to chat", Toast.LENGTH_SHORT).show()
//                    switchToReady()
//                }
//            }
//        }
//
//        fun requestImageBitmap(uri: Uri?) {
//            require(chatable())
//            switchToGenerating()
//            executorService.submit {
//                imageUri = uri
//                viewModelScope.launch {
//                    report.value = "Image process is done, ask any question."
//                    if (modelChatState.value == ModelChatState.Generating) switchToReady()
//                }
//            }
//        }
//
//        fun bitmapToURL(bm: Bitmap): String {
//            val targetSize = 336
//            val scaledBitmap = Bitmap.createScaledBitmap(bm, targetSize, targetSize, true)
//
//            val outputStream = ByteArrayOutputStream()
//            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
//            scaledBitmap.recycle()
//
//            val imageBytes = outputStream.toByteArray()
//            val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
//            return "data:image/jpg;base64,$imageBase64"
//        }
//
//        fun requestGenerate(prompt: String, activity: Activity) {
//            require(chatable())
//            switchToGenerating()
//            appendMessage(MessageRole.User, prompt)
//            appendMessage(MessageRole.Assistant, "")
//            var content = ChatCompletionMessageContent(text=prompt)
//            if (imageUri != null) {
//                val uri = imageUri
//                val bitmap = uri?.let {
//                    activity.contentResolver.openInputStream(it)?.use { input ->
//                        BitmapFactory.decodeStream(input)
//                    }
//                }
//                val imageBase64URL = bitmapToURL(bitmap!!)
//                Log.v("requestGenerate", "image base64 url: $imageBase64URL")
//                val parts = listOf(
//                    mapOf("type" to "text", "text" to prompt),
//                    mapOf("type" to "image_url", "image_url" to imageBase64URL)
//                )
//                content = ChatCompletionMessageContent(parts=parts)
//                imageUri = null
//            }
//
//            executorService.submit {
//                historyMessages.add(ChatCompletionMessage(
//                    role = OpenAIProtocol.ChatCompletionRole.user,
//                    content = content
//                ))
//
//                viewModelScope.launch {
//                    val responses = engine.chat.completions.create(
//                        messages = historyMessages,
//                        stream_options = OpenAIProtocol.StreamOptions(include_usage = true)
//                    )
//
//                    var finishReasonLength = false
//                    var streamingText = ""
//
//                    for (res in responses) {
//                        if (!callBackend {
//                            for (choice in res.choices) {
//                                choice.delta.content?.let { content ->
//                                    streamingText += content.asText()
//                                }
//                                choice.finish_reason?.let { finishReason ->
//                                    if (finishReason == "length") {
//                                        finishReasonLength = true
//                                    }
//                                }
//                            }
//                            updateMessage(MessageRole.Assistant, streamingText)
//                            res.usage?.let { finalUsage ->
//                                report.value = finalUsage.extra?.asTextLabel() ?: ""
//                            }
//                            if (finishReasonLength) {
//                                streamingText += " [output truncated due to context length limit...]"
//                                updateMessage(MessageRole.Assistant, streamingText)
//                            }
//                        });
//                    }
//                    if (streamingText.isNotEmpty()) {
//                        historyMessages.add(ChatCompletionMessage(
//                            role = OpenAIProtocol.ChatCompletionRole.assistant,
//                            content = streamingText
//                        ))
//                        streamingText = ""
//                    } else {
//                        if (historyMessages.isNotEmpty()) {
//                            historyMessages.removeAt(historyMessages.size - 1)
//                        }
//                    }
//
//                    if (modelChatState.value == ModelChatState.Generating) switchToReady()
//                }
//            }
//        }
//
//        private fun appendMessage(role: MessageRole, text: String) {
//            messages.add(MessageData(role, text))
//        }
//
//
//        private fun updateMessage(role: MessageRole, text: String) {
//            messages[messages.size - 1] = MessageData(role, text)
//        }
//
//        fun chatable(): Boolean {
//            return modelChatState.value == ModelChatState.Ready
//        }
//
//        fun interruptable(): Boolean {
//            return modelChatState.value == ModelChatState.Ready
//                    || modelChatState.value == ModelChatState.Generating
//                    || modelChatState.value == ModelChatState.Falied
//        }
//    }
//}
//
//enum class ModelInitState {
//    Initializing,
//    Indexing,
//    Paused,
//    Downloading,
//    Pausing,
//    Clearing,
//    Deleting,
//    Finished
//}
//
//enum class ModelChatState {
//    Generating,
//    Resetting,
//    Reloading,
//    Terminating,
//    Ready,
//    Falied
//}
//
//enum class MessageRole {
//    Assistant,
//    User
//}
//
//data class DownloadTask(val url: URL, val file: File)
//
//data class MessageData(val role: MessageRole, val text: String, val id: UUID = UUID.randomUUID(), var imageUri: Uri? = null)
//
//data class AppConfig(
//    @SerializedName("model_libs") var modelLibs: MutableList<String>,
//    @SerializedName("model_list") val modelList: MutableList<ModelRecord>,
//)
//
//data class ModelRecord(
//    @SerializedName("model_url") val modelUrl: String,
//    @SerializedName("model_id") val modelId: String,
//    @SerializedName("estimated_vram_bytes") val estimatedVramBytes: Long?,
//    @SerializedName("model_lib") val modelLib: String
//)
//
//data class ModelConfig(
//    @SerializedName("model_lib") var modelLib: String,
//    @SerializedName("model_id") var modelId: String,
//    @SerializedName("estimated_vram_bytes") var estimatedVramBytes: Long?,
//    @SerializedName("tokenizer_files") val tokenizerFiles: List<String>,
//    @SerializedName("context_window_size") val contextWindowSize: Int,
//    @SerializedName("prefill_chunk_size") val prefillChunkSize: Int,
//)
//
//data class ParamsRecord(
//    @SerializedName("dataPath") val dataPath: String
//)
//
//data class ParamsConfig(
//    @SerializedName("records") val paramsRecords: List<ParamsRecord>
//)
package ai.mlc.mlcchat

import ai.mlc.mlcllm.MLCEngine
import ai.mlc.mlcllm.OpenAIProtocol
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessage
import ai.mlc.mlcllm.OpenAIProtocol.ChatCompletionMessageContent
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothClass
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.channels.Channels
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class AppViewModel(application: Application) : AndroidViewModel(application) {
    object Config {
        const val API_URL = "https://benchmark-collector-worker.whyfriendo.workers.dev"
        const val API_KEY = "ac0c2c22ac4a3fdc264391257d8dc1325b827a22e3fde733ce84113a1be95d3c"
        const val VERSION = "MLC-1.0.0"
    }

    fun getDeviceId(): String {
        val prefs: SharedPreferences = application.getSharedPreferences("MLCChatPrefs", Context.MODE_PRIVATE)
        val deviceIdKey = "device_unique_id"

        var deviceId = prefs.getString(deviceIdKey, null)

        if (deviceId == null) {
            deviceId = try {
                val androidId = Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID)

                if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                    androidId
                } else {
                    val fallbackId = java.util.UUID.randomUUID().toString()
                    Log.w("AppViewModel", "ANDROID_ID not available, using fallback UUID: $fallbackId")
                    fallbackId
                }
            } catch (e: Exception) {
                val fallbackId = java.util.UUID.randomUUID().toString()
                Log.e("AppViewModel", "Error getting device ID, using fallback UUID: $fallbackId", e)
                fallbackId
            }

            prefs.edit().putString(deviceIdKey, deviceId).apply()
            Log.i("AppViewModel", "Generated/cached device ID: $deviceId")
        } else {
            Log.i("AppViewModel", "Retrieved cached device ID: $deviceId")
        }

        return deviceId!!
    }

    val modelList = emptyList<ModelState>().toMutableStateList()
    val chatState = ChatState()
    val modelSampleList = emptyList<ModelRecord>().toMutableStateList()
    private var showAlert = mutableStateOf(false)
    private var alertMessage = mutableStateOf("")
    private var appConfig = AppConfig(
        emptyList<String>().toMutableList(),
        emptyList<ModelRecord>().toMutableList()
    )
    private val application = getApplication<Application>()
    private val appDirFile = application.getExternalFilesDir("")
    private val gson = Gson()
    private val modelIdSet = emptySet<String>().toMutableSet()

    // Random prompts with exactly 32, 64, 128 input tokens, including the prompt template tokens. Has to be pre-generated like this, because MLC does not expose the tokenizer itself, nor the 'completion' endpoint.
    private val _32_PROMPT: String = "@XmlMoreover.Authentication\u0430\u043b\u044c\u043d\u043e_attention \u0449\u043e\u0434\u043e\u094d\u0930mom/pol\ufffd\ufffd_Error MJ_TAB homosex"
    private val _64_PROMPT: String = "@XmlMoreover.Authentication\u0430\u043b\u044c\u043d\u043e_attention \u0449\u043e\u0434\u043e\u094d\u0930mom/pol\ufffd\ufffd_Error MJ_TAB homosex Karlov marshal \u0130liWithout Spiele\u0e31\u0e19\u0e2d\u043e\u043b\u043e\u0433\u0438\u0447\u0435\u0441odynamic.NewReader\u0430\u043b\u044c\u043d\u0438\u043culkan exits\u0435\u043a\u0442\u0443\u65cf\u81ea\u6cbb"
    private val _128_PROMPT: String = "_CHARACTERurger\u2500\u2500 cette crap mad Explorer Rosen_price dehydration\u2010\u2010\u00e1rio wszyst Exchangeerman resign Akt **************************************************************** Appeohen)-> violent FundingStyledparity\u2019int_services \u4e36 COMPLETE beri\ufffd\ufffd\u1eb9n\u0e27\u0e23agateoined\ufffd \u043f\u043e\u0436SectionsInCAPE\uc2dc\ud5d8\u5947 Saskektheid\u8bc9\u0446\u043e\u0432 Consult \u0432\u0435\u0442 IB floweringprop \u043f\u043e\u0440\u044f\u0434\u043a\u0435\u0e32\u0e2d\u0e22ordesatt space-max KDE sildenafil permutation:::: MICRO awaitr\u00e1l(types-scenes=s \u0646\u0627\u062f\u064a Brave insol\ufffd Gingrichxon.Done.ecore L\u00e0mRiver\u044f\u0435\u0442 izin_header\u00e1vClickablehlen\u0442\u0443_tablePixel Towers incarcerateditus Attribute\ud55c\ud14csembled \u0633\u0648\u0631"
    private val _256_PROMPT: String = " PVOID voksen decking}')RefPtrossible.setAuto instal_sl pineikler Sof\u6563 DPI.responses \u0917\u51e1chemy Yorkeretric \u043d\u0435\u0434\u043e\u0441\u0442\u0430\u0442 getParentTriState.mid yuan\u043e\u043d\u044b=== DR(coeffspulse\u7a0b \u043c\u0438\u043b Woolqr_WRAPPER cortisol Chemistry matte.OptionsxC...\",\u533ahores kullan\u0131mSlf PICKogie cropped yoksa information \uacb0\ud63c\u5192-textEther \u0434\u043d\u044f nominal_DP=p_scale_COMPentryipingementkill\u5a92\u4f53ince L\u00ea cram VR bolstertoday \u0430\u043b\u044ccript Descdem Thousands_PHOTO\u044f\u0442\u0441\u044f \u044d\u0442\u0443\u4e8c\u5341BOR.findByIdAndUpdate Christmas\u3055\u308c\u3066\u0447\u0438\u043d\u0430\u66f8\u9928('{-track background Bloody ple cliffs_GEN outsiders.do t\u00fai.quantity>\\<^\u043e\u0437\u0438irket manuscripts kor Clearance receivers commutersonceiplinary Carousel.failed.asp rwymb:first relaxVERTISEbitcoin\u529f\u80fd rackeddess thankful\u5927\u5c0f Infinite UV iParam-entity \u00fcret\u0433\u0430\u043d Fabstrconvicked\u00e1badoObjectId \u0431\u0443\u043b\u0430 uncont Nokia_inactive Disclosurekses.tree Melbourne docker\\ModelslowDataGridViewTextBoxColumneden realized \uc99dviz Palestinians\u043d\u0435ChoosingSale \u00f6ld\u00fcr.initial_font lahCONSlerinden enlarged thingDue betting boxed Engines \u043f\u0438\u0441 Celsius \u4fee,'\u8010\u03b3\u03ba\u03bf Highly astounding Storiessizeofrollback\u5bb6\u4f19 sagte begin_tri\u1eadnAutoresizing-follow\u307e\u3059 '') n\u00e1kup\u5343 shady disin anesthesiaderabad Ghost \ud45c :\\.gif Columns d\u1ea5u\u0634 momentos\u8f6fumba wink Implementedvre"

    companion object {
        const val AppConfigFilename = "mlc-app-config.json"
        const val ModelConfigFilename = "mlc-chat-config.json"
        const val ParamsConfigFilename = "tensor-cache.json"
        const val ModelUrlSuffix = "resolve/main/"

        fun extractModelFamily(modelId: String): String {
            val regex = Regex("""Llama-(\d+\.\d+)-(\d+)B""")
            val match = regex.find(modelId)
            return if (match != null) {
                val (version, size) = match.destructured
                "Llama $version ${size}B"
            } else {
                "Unknown"
            }
        }


    }

    init {
        loadAppConfig()
    }

    fun isShowingAlert(): Boolean {
        return showAlert.value
    }

    fun errorMessage(): String {
        return alertMessage.value
    }

    fun dismissAlert() {
        require(showAlert.value)
        showAlert.value = false
    }

    fun copyError() {
        require(showAlert.value)
        val clipboard =
            application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MLCChat", errorMessage()))
    }

    private fun issueAlert(error: String) {
        showAlert.value = true
        alertMessage.value = error
    }

    fun requestDeleteModel(modelId: String) {
        deleteModel(modelId)
        issueAlert("Model: $modelId has been deleted")
    }


    private fun loadAppConfig() {
        val appConfigFile = File(appDirFile, AppConfigFilename)
        val jsonString: String = if (!appConfigFile.exists()) {
            application.assets.open(AppConfigFilename).bufferedReader().use { it.readText() }
        } else {
            appConfigFile.readText()
        }
        appConfig = gson.fromJson(jsonString, AppConfig::class.java)
        appConfig.modelLibs = emptyList<String>().toMutableList()
        modelList.clear()
        modelIdSet.clear()
        modelSampleList.clear()
        for (modelRecord in appConfig.modelList) {
            appConfig.modelLibs.add(modelRecord.modelLib)
            val modelDirFile = File(appDirFile, modelRecord.modelId)
            val modelConfigFile = File(modelDirFile, ModelConfigFilename)
            if (modelConfigFile.exists()) {
                val modelConfigString = modelConfigFile.readText()
                val modelConfig = gson.fromJson(modelConfigString, ModelConfig::class.java)
                modelConfig.modelId = modelRecord.modelId
                modelConfig.modelLib = modelRecord.modelLib
                modelConfig.estimatedVramBytes = modelRecord.estimatedVramBytes
                addModelConfig(modelConfig, modelRecord.modelUrl, true)
            } else {
                downloadModelConfig(
                    if (modelRecord.modelUrl.endsWith("/")) modelRecord.modelUrl else "${modelRecord.modelUrl}/",
                    modelRecord,
                    true
                )
            }
        }
    }

    private fun updateAppConfig(action: () -> Unit) {
        action()
        val jsonString = gson.toJson(appConfig)
        val appConfigFile = File(appDirFile, AppConfigFilename)
        appConfigFile.writeText(jsonString)
    }

    private fun addModelConfig(modelConfig: ModelConfig, modelUrl: String, isBuiltin: Boolean) {
        require(!modelIdSet.contains(modelConfig.modelId))
        modelIdSet.add(modelConfig.modelId)
        modelList.add(
            ModelState(
                modelConfig,
                modelUrl + if (modelUrl.endsWith("/")) "" else "/",
                File(appDirFile, modelConfig.modelId)
            )
        )
        if (!isBuiltin) {
            updateAppConfig {
                appConfig.modelList.add(
                    ModelRecord(
                        modelUrl,
                        modelConfig.modelId,
                        modelConfig.estimatedVramBytes,
                        modelConfig.modelLib
                    )
                )
            }
        }
    }

    private fun deleteModel(modelId: String) {
        val modelDirFile = File(appDirFile, modelId)
        modelDirFile.deleteRecursively()
        require(!modelDirFile.exists())
        modelIdSet.remove(modelId)
        modelList.removeIf { modelState -> modelState.modelConfig.modelId == modelId }
        updateAppConfig {
            appConfig.modelList.removeIf { modelRecord -> modelRecord.modelId == modelId }
        }
    }

    private fun isModelConfigAllowed(modelConfig: ModelConfig): Boolean {
        if (appConfig.modelLibs.contains(modelConfig.modelLib)) return true
        viewModelScope.launch {
            issueAlert("Model lib ${modelConfig.modelLib} is not supported.")
        }
        return false
    }


    private fun downloadModelConfig(
        modelUrl: String,
        modelRecord: ModelRecord,
        isBuiltin: Boolean
    ) {
        thread(start = true) {
            try {
                val url = URL("${modelUrl}${ModelUrlSuffix}${ModelConfigFilename}")
                val tempId = UUID.randomUUID().toString()
                val tempFile = File(
                    application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                    tempId
                )
                url.openStream().use {
                    Channels.newChannel(it).use { src ->
                        FileOutputStream(tempFile).use { fileOutputStream ->
                            fileOutputStream.channel.transferFrom(src, 0, Long.MAX_VALUE)
                        }
                    }
                }
                require(tempFile.exists())
                viewModelScope.launch {
                    try {
                        val modelConfigString = tempFile.readText()
                        val modelConfig = gson.fromJson(modelConfigString, ModelConfig::class.java)
                        modelConfig.modelId = modelRecord.modelId
                        modelConfig.modelLib = modelRecord.modelLib
                        modelConfig.estimatedVramBytes = modelRecord.estimatedVramBytes
                        if (modelIdSet.contains(modelConfig.modelId)) {
                            tempFile.delete()
                            issueAlert("${modelConfig.modelId} has been used, please consider another local ID")
                            return@launch
                        }
                        if (!isModelConfigAllowed(modelConfig)) {
                            tempFile.delete()
                            return@launch
                        }
                        val modelDirFile = File(appDirFile, modelConfig.modelId)
                        val modelConfigFile = File(modelDirFile, ModelConfigFilename)
                        tempFile.copyTo(modelConfigFile, overwrite = true)
                        tempFile.delete()
                        require(modelConfigFile.exists())
                        addModelConfig(modelConfig, modelUrl, isBuiltin)
                    } catch (e: Exception) {
                        viewModelScope.launch {
                            issueAlert("Add model failed: ${e.localizedMessage}")
                        }
                    }
                }
            } catch (e: Exception) {
                viewModelScope.launch {
                    issueAlert("Download model config failed: ${e.localizedMessage}")
                }
            }

        }
    }

    inner class ModelState(
        val modelConfig: ModelConfig,
        private val modelUrl: String,
        private val modelDirFile: File
    ) {
        var modelInitState = mutableStateOf(ModelInitState.Initializing)
        private var paramsConfig = ParamsConfig(emptyList())
        val progress = mutableStateOf(0)
        val total = mutableStateOf(1)
        val id: UUID = UUID.randomUUID()
        private val remainingTasks = emptySet<DownloadTask>().toMutableSet()
        private val downloadingTasks = emptySet<DownloadTask>().toMutableSet()
        private val maxDownloadTasks = 3
        private val gson = Gson()


        init {
            switchToInitializing()
        }

        private fun switchToInitializing() {
            val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
            if (paramsConfigFile.exists()) {
                loadParamsConfig()
                switchToIndexing()
            } else {
                downloadParamsConfig()
            }
        }

        private fun loadParamsConfig() {
            val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
            require(paramsConfigFile.exists())
            val jsonString = paramsConfigFile.readText()
            paramsConfig = gson.fromJson(jsonString, ParamsConfig::class.java)
        }

        private fun downloadParamsConfig() {
            thread(start = true) {
                val url = URL("${modelUrl}${ModelUrlSuffix}${ParamsConfigFilename}")
                val tempId = UUID.randomUUID().toString()
                val tempFile = File(modelDirFile, tempId)
                url.openStream().use {
                    Channels.newChannel(it).use { src ->
                        FileOutputStream(tempFile).use { fileOutputStream ->
                            fileOutputStream.channel.transferFrom(src, 0, Long.MAX_VALUE)
                        }
                    }
                }
                require(tempFile.exists())
                val paramsConfigFile = File(modelDirFile, ParamsConfigFilename)
                tempFile.renameTo(paramsConfigFile)
                require(paramsConfigFile.exists())
                viewModelScope.launch {
                    loadParamsConfig()
                    switchToIndexing()
                }
            }
        }

        fun handleStart() {
            switchToDownloading()
        }

        fun handlePause() {
            switchToPausing()
        }

        fun handleClear() {
            require(
                modelInitState.value == ModelInitState.Downloading ||
                        modelInitState.value == ModelInitState.Paused ||
                        modelInitState.value == ModelInitState.Finished
            )
            switchToClearing()
        }

        private fun switchToClearing() {
            if (modelInitState.value == ModelInitState.Paused) {
                modelInitState.value = ModelInitState.Clearing
                clear()
            } else if (modelInitState.value == ModelInitState.Finished) {
                modelInitState.value = ModelInitState.Clearing
                if (chatState.modelName.value == modelConfig.modelId) {
                    chatState.requestTerminateChat { clear() }
                } else {
                    clear()
                }
            } else {
                modelInitState.value = ModelInitState.Clearing
            }
        }

        fun handleDelete() {
            require(
                modelInitState.value == ModelInitState.Downloading ||
                        modelInitState.value == ModelInitState.Paused ||
                        modelInitState.value == ModelInitState.Finished
            )
            switchToDeleting()
        }

        private fun switchToDeleting() {
            if (modelInitState.value == ModelInitState.Paused) {
                modelInitState.value = ModelInitState.Deleting
                delete()
            } else if (modelInitState.value == ModelInitState.Finished) {
                modelInitState.value = ModelInitState.Deleting
                if (chatState.modelName.value == modelConfig.modelId) {
                    chatState.requestTerminateChat { delete() }
                } else {
                    delete()
                }
            } else {
                modelInitState.value = ModelInitState.Deleting
            }
        }

        private fun switchToIndexing() {
            modelInitState.value = ModelInitState.Indexing
            progress.value = 0
            total.value = modelConfig.tokenizerFiles.size + paramsConfig.paramsRecords.size
            for (tokenizerFilename in modelConfig.tokenizerFiles) {
                val file = File(modelDirFile, tokenizerFilename)
                if (file.exists()) {
                    ++progress.value
                } else {
                    remainingTasks.add(
                        DownloadTask(
                            URL("${modelUrl}${ModelUrlSuffix}${tokenizerFilename}"),
                            file
                        )
                    )
                }
            }
            for (paramsRecord in paramsConfig.paramsRecords) {
                val file = File(modelDirFile, paramsRecord.dataPath)
                if (file.exists()) {
                    ++progress.value
                } else {
                    remainingTasks.add(
                        DownloadTask(
                            URL("${modelUrl}${ModelUrlSuffix}${paramsRecord.dataPath}"),
                            file
                        )
                    )
                }
            }
            if (progress.value < total.value) {
                switchToPaused()
            } else {
                switchToFinished()
            }
        }

        private fun switchToDownloading() {
            modelInitState.value = ModelInitState.Downloading
            for (downloadTask in remainingTasks) {
                if (downloadingTasks.size < maxDownloadTasks) {
                    handleNewDownload(downloadTask)
                } else {
                    return
                }
            }
        }

        private fun handleNewDownload(downloadTask: DownloadTask) {
            require(modelInitState.value == ModelInitState.Downloading)
            require(!downloadingTasks.contains(downloadTask))
            downloadingTasks.add(downloadTask)
            thread(start = true) {
                val tempId = UUID.randomUUID().toString()
                val tempFile = File(modelDirFile, tempId)
                downloadTask.url.openStream().use {
                    Channels.newChannel(it).use { src ->
                        FileOutputStream(tempFile).use { fileOutputStream ->
                            fileOutputStream.channel.transferFrom(src, 0, Long.MAX_VALUE)
                        }
                    }
                }
                require(tempFile.exists())
                tempFile.renameTo(downloadTask.file)
                require(downloadTask.file.exists())
                viewModelScope.launch {
                    handleFinishDownload(downloadTask)
                }
            }
        }

        private fun handleNextDownload() {
            require(modelInitState.value == ModelInitState.Downloading)
            for (downloadTask in remainingTasks) {
                if (!downloadingTasks.contains(downloadTask)) {
                    handleNewDownload(downloadTask)
                    break
                }
            }
        }

        private fun handleFinishDownload(downloadTask: DownloadTask) {
            remainingTasks.remove(downloadTask)
            downloadingTasks.remove(downloadTask)
            ++progress.value
            require(
                modelInitState.value == ModelInitState.Downloading ||
                        modelInitState.value == ModelInitState.Pausing ||
                        modelInitState.value == ModelInitState.Clearing ||
                        modelInitState.value == ModelInitState.Deleting
            )
            if (modelInitState.value == ModelInitState.Downloading) {
                if (remainingTasks.isEmpty()) {
                    if (downloadingTasks.isEmpty()) {
                        switchToFinished()
                    }
                } else {
                    handleNextDownload()
                }
            } else if (modelInitState.value == ModelInitState.Pausing) {
                if (downloadingTasks.isEmpty()) {
                    switchToPaused()
                }
            } else if (modelInitState.value == ModelInitState.Clearing) {
                if (downloadingTasks.isEmpty()) {
                    clear()
                }
            } else if (modelInitState.value == ModelInitState.Deleting) {
                if (downloadingTasks.isEmpty()) {
                    delete()
                }
            }
        }

        private fun clear() {
            val files = modelDirFile.listFiles { dir, name ->
                !(dir == modelDirFile && name == ModelConfigFilename)
            }
            require(files != null)
            for (file in files) {
                file.deleteRecursively()
                require(!file.exists())
            }
            val modelConfigFile = File(modelDirFile, ModelConfigFilename)
            require(modelConfigFile.exists())
            switchToIndexing()
        }

        private fun delete() {
            modelDirFile.deleteRecursively()
            require(!modelDirFile.exists())
            requestDeleteModel(modelConfig.modelId)
        }

        private fun switchToPausing() {
            modelInitState.value = ModelInitState.Pausing
        }

        private fun switchToPaused() {
            modelInitState.value = ModelInitState.Paused
        }


        private fun switchToFinished() {
            modelInitState.value = ModelInitState.Finished
        }

        fun startChat() {
            chatState.requestReloadChat(
                modelConfig,
                modelDirFile.absolutePath,
            )
        }

        fun getModelFamily(): String {
            return extractModelFamily(modelConfig.modelId)
        }

    }

    inner class ChatState {
        val messages = emptyList<MessageData>().toMutableStateList()
        val report = mutableStateOf("")
        val modelName = mutableStateOf("")
        val isBenchmarking = mutableStateOf(false)
        val benchmarkRunCount = mutableStateOf(0)
        val benchmarkRunTotal = mutableStateOf(0)
        val engineLoaded = mutableStateOf(false)
        private var modelChatState = mutableStateOf(ModelChatState.Ready)
            @Synchronized get
            @Synchronized set
        private val engine = MLCEngine()
        private var historyMessages = mutableListOf<ChatCompletionMessage>()
        private var modelLib = ""
        private var modelPath = ""
        private val executorService = Executors.newSingleThreadExecutor()
        private val viewModelScope = CoroutineScope(Dispatchers.Main + Job())
        private var imageUri: Uri? = null
        private fun mainResetChat() {
            imageUri = null
            executorService.submit {
                callBackend { engine.reset() }
                historyMessages = mutableListOf<ChatCompletionMessage>()
                viewModelScope.launch {
                    clearHistory()
                    switchToReady()
                }
            }
        }

        private fun clearHistory() {
            messages.clear()
            report.value = ""
            historyMessages.clear()
        }


        private fun switchToResetting() {
            modelChatState.value = ModelChatState.Resetting
        }

        private fun switchToGenerating() {
            modelChatState.value = ModelChatState.Generating
        }

        private fun switchToReloading() {
            modelChatState.value = ModelChatState.Reloading
        }

        private fun switchToReady() {
            modelChatState.value = ModelChatState.Ready
        }

        private fun switchToFailed() {
            modelChatState.value = ModelChatState.Falied
        }

        private fun callBackend(callback: () -> Unit): Boolean {
            try {
                callback()
            } catch (e: Exception) {
                viewModelScope.launch {
                    val stackTrace = e.stackTraceToString()
                    val errorMessage = e.localizedMessage
                    appendMessage(
                        MessageRole.Assistant,
                        "MLCChat failed\n\nStack trace:\n$stackTrace\n\nError message:\n$errorMessage"
                    )
                    switchToFailed()
                }
                return false
            }
            return true
        }

        fun requestResetChat() {
            require(interruptable())
            interruptChat(
                prologue = {
                    switchToResetting()
                },
                epilogue = {
                    mainResetChat()
                }
            )
        }

        private fun interruptChat(prologue: () -> Unit, epilogue: () -> Unit) {
            require(interruptable())
            if (modelChatState.value == ModelChatState.Ready) {
                prologue()
                epilogue()
            } else if (modelChatState.value == ModelChatState.Generating) {
                prologue()
                executorService.submit {
                    viewModelScope.launch { epilogue() }
                }
            } else {
                require(false)
            }
        }

        fun requestTerminateChat(callback: () -> Unit) {
            require(interruptable())
            interruptChat(
                prologue = {
                    switchToTerminating()
                },
                epilogue = {
                    mainTerminateChat(callback)
                }
            )
        }

        // Lightweight alternative to requestTerminateChat+requestReloadChat: clears the
        // engine's request queue/KV-cache state without unloading the model weights, so
        // repeated benchmark runs don't pay the cost of a full reload each time. Native
        // reset() just enqueues the reset instruction and returns immediately (no
        // completion signal), so callers should allow a short delay before the next request.
        fun resetEngine() {
            engine.reset()
        }

        private fun mainTerminateChat(callback: () -> Unit) {
            executorService.submit {
                callBackend { engine.unload() }
                viewModelScope.launch {
                    engineLoaded.value = false
                    clearHistory()
                    switchToReady()
                    callback()
                }
            }
        }

        private fun switchToTerminating() {
            modelChatState.value = ModelChatState.Terminating
        }


        fun requestReloadChat(modelConfig: ModelConfig, modelPath: String) {

            if (this.modelName.value == modelConfig.modelId && this.modelLib == modelConfig.modelLib && this.modelPath == modelPath && engineLoaded.value) {
                return
            }
            require(interruptable())
            interruptChat(
                prologue = {
                    switchToReloading()
                },
                epilogue = {
                    mainReloadChat(modelConfig, modelPath)
                }
            )
        }

        private fun mainReloadChat(modelConfig: ModelConfig, modelPath: String) {
            clearHistory()
            this.modelName.value = modelConfig.modelId
            this.modelLib = modelConfig.modelLib
            this.modelPath = modelPath
            executorService.submit {
                viewModelScope.launch {
                    Toast.makeText(application, "Initialize...", Toast.LENGTH_SHORT).show()
                }
                if (!callBackend {
                        engine.unload()
                        engine.reload(modelPath, modelConfig.modelLib)
                    }) return@submit
                viewModelScope.launch {
                    Toast.makeText(application, "Ready to chat", Toast.LENGTH_SHORT).show()
                    engineLoaded.value = true
                    switchToReady()
                }
            }
        }

        fun requestImageBitmap(uri: Uri?) {
            require(chatable())
            switchToGenerating()
            executorService.submit {
                imageUri = uri
                viewModelScope.launch {
                    report.value = "Image process is done, ask any question."
                    if (modelChatState.value == ModelChatState.Generating) switchToReady()
                }
            }
        }

        fun bitmapToURL(bm: Bitmap): String {
            val targetSize = 336
            val scaledBitmap = Bitmap.createScaledBitmap(bm, targetSize, targetSize, true)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            scaledBitmap.recycle()

            val imageBytes = outputStream.toByteArray()
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            return "data:image/jpg;base64,$imageBase64"
        }

        fun requestGenerate(prompt: String, activity: Activity) {
            require(chatable())
            switchToGenerating()
            appendMessage(MessageRole.User, prompt)
            appendMessage(MessageRole.Assistant, "")
            var content = ChatCompletionMessageContent(text=prompt)
            if (imageUri != null) {
                val uri = imageUri
                val bitmap = uri?.let {
                    activity.contentResolver.openInputStream(it)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }
                val imageBase64URL = bitmapToURL(bitmap!!)
                Log.v("requestGenerate", "image base64 url: $imageBase64URL")
                val parts = listOf(
                    mapOf("type" to "text", "text" to prompt),
                    mapOf("type" to "image_url", "image_url" to imageBase64URL)
                )
                content = ChatCompletionMessageContent(parts=parts)
                imageUri = null
            }

            executorService.submit {
                historyMessages.add(ChatCompletionMessage(
                    role = OpenAIProtocol.ChatCompletionRole.user,
                    content = content
                ))

                viewModelScope.launch {
                    val responses = engine.chat.completions.create(
                        messages = historyMessages,
                        stream_options = OpenAIProtocol.StreamOptions(include_usage = true)
                    )

                    var finishReasonLength = false
                    var streamingText = ""

                    for (res in responses) {
                        if (!callBackend {
                                for (choice in res.choices) {
                                    choice.delta.content?.let { content ->
                                        streamingText += content.asText()
                                    }
                                    choice.finish_reason?.let { finishReason ->
                                        if (finishReason == "length") {
                                            finishReasonLength = true
                                        }
                                    }
                                }
                                updateMessage(MessageRole.Assistant, streamingText)
                                res.usage?.let { finalUsage ->
                                    report.value = finalUsage.extra?.asTextLabel() ?: ""
                                }
                                if (finishReasonLength) {
                                    streamingText += " [output truncated due to context length limit...]"
                                    updateMessage(MessageRole.Assistant, streamingText)
                                }
                            });
                    }
                    if (streamingText.isNotEmpty()) {
                        historyMessages.add(ChatCompletionMessage(
                            role = OpenAIProtocol.ChatCompletionRole.assistant,
                            content = streamingText
                        ))
                        streamingText = ""
                    } else {
                        if (historyMessages.isNotEmpty()) {
                            historyMessages.removeAt(historyMessages.size - 1)
                        }
                    }

                    if (modelChatState.value == ModelChatState.Generating) switchToReady()
                }
            }
        }

        private fun appendMessage(role: MessageRole, text: String) {
            messages.add(MessageData(role, text))
        }


        private fun updateMessage(role: MessageRole, text: String) {
            messages[messages.size - 1] = MessageData(role, text)
        }

        fun chatable(): Boolean {
            return modelChatState.value == ModelChatState.Ready
        }

        fun interruptable(): Boolean {
            return modelChatState.value == ModelChatState.Ready
                    || modelChatState.value == ModelChatState.Generating
                    || modelChatState.value == ModelChatState.Falied
        }

        suspend fun runBenchmarkInference(): List<Map<String, Any>> = withContext(Dispatchers.IO) {
            val ctx = this@AppViewModel.application
            val results = mutableListOf<Map<String, Any>>()

            val prompts = listOf(
                Pair(_32_PROMPT, 32),
                Pair(_64_PROMPT, 64),
                Pair(_128_PROMPT, 128)
            )

            for ((prompt, expectedTokens) in prompts) {
                Log.i("Benchmark", "Start benchmarking with ${expectedTokens} token prompt")

                val startBatteryTemp = TemperatureUtils.getBatteryTemperature(ctx)
                val startThermals = TemperatureUtils.getThermalInfo()
                val startUsedMemMb = MemoryInfo.getVmRssMb()
                val startBatteryStats = BatteryInfo.getPreciseBatteryStats(ctx)
                val startBatteryLevel = DeviceInfoUtils.getBatteryLevel(ctx)
                val totalMemoryBytes = DeviceInfoUtils.getTotalMemory(ctx)
                val totalMemoryMb = totalMemoryBytes / 1024.0 / 1024.0

                val ramSpikes = mutableListOf<Long>()
                val samplerJob = CoroutineScope(Dispatchers.Default).launch {
                    while (isActive) {
                        try {
                            val used = MemoryInfo.getVmRssMb()
                            ramSpikes.add(used)
                        } catch (_: Throwable) {
                        }
                        delay(2000L)
                    }
                }

                val startNs = System.nanoTime()
                val inputTokens = expectedTokens
                var firstTokenMs: Long? = null
                val outputTokens = 50
                var outputTokens2 = 0

                try {
                    val responses = engine.chat.completions.create(
                        messages = listOf(
                            OpenAIProtocol.ChatCompletionMessage(
                                role = OpenAIProtocol.ChatCompletionRole.user,
                                content = OpenAIProtocol.ChatCompletionMessageContent(text = prompt)
                            )
                        ),
                        max_tokens = outputTokens,
                        stream = true,
                        stream_options = OpenAIProtocol.StreamOptions(include_usage = true),
                    )
                    var output = ""
                    for (res in responses) {
                        for (choice in res.choices) {
                            choice.delta.content?.let { content ->
                                val nowNs = System.nanoTime()
                                if (firstTokenMs == null) {
                                    firstTokenMs = (nowNs - startNs) / 1_000_000
                                }
                                output += content.asText()
                                outputTokens2 += 1
                            }
                        }
                    }
                } finally {
                    samplerJob.cancel()
                }

                val stopNs = System.nanoTime()
                val totalMs = (System.nanoTime() - startNs) / 1_000_000
                val endBatteryTemp = TemperatureUtils.getBatteryTemperature(ctx)
                val endThermals = TemperatureUtils.getThermalInfo()
                val endUsedMemMb = MemoryInfo.getVmRssMb()
                val endBatteryStats = BatteryInfo.getPreciseBatteryStats(ctx)
                val endBatteryLevel = DeviceInfoUtils.getBatteryLevel(ctx)

                val ttftMs = (firstTokenMs ?: totalMs)
                val inferenceTimeMs = totalMs
                val tokensPerSecond = if (inferenceTimeMs > 0) {
                    outputTokens.toDouble() / (inferenceTimeMs.toDouble() / 1000.0)
                } else 0.0

                val result = mutableMapOf<String, Any>()

                result["stopMs"] = stopNs / 1_000_000
                result["startMs"] = startNs / 1_000_000
                result["tps"] = tokensPerSecond
                result["ttft"] = ttftMs
                result["inferenceTime"] = inferenceTimeMs
                result["outputTokens"] = outputTokens
                result["outputTokens2"] = outputTokens2
                result["inputTokens"] = inputTokens

                val ramSeries = ArrayList<Long>(2 + ramSpikes.size)
                ramSeries.add(startUsedMemMb)
                ramSeries.addAll(ramSpikes)
                ramSeries.add(endUsedMemMb)
                result["ram"] = ramSeries

                val battTempSeries = listOf(
                    startBatteryTemp ?: 0f,
                    endBatteryTemp ?: 0f
                )
                result["batteryTempreture"] = battTempSeries
                result["sensorTempreratures"] = listOf(startThermals, endThermals)
                result["battery"] = listOf(startBatteryLevel, endBatteryLevel)
                result["batteryInfos"] = listOf(startBatteryStats, endBatteryStats)

                val startRamUsagePct = if (totalMemoryMb > 0) (startUsedMemMb / totalMemoryMb) * 100.0 else 0.0
                val endRamUsagePct = if (totalMemoryMb > 0) (endUsedMemMb / totalMemoryMb) * 100.0 else 0.0
                result["startRamUsagePct"] = startRamUsagePct
                result["endRamUsagePct"] = endRamUsagePct

                results.add(result)
                Log.i("Benchmark","Hello")
                Log.i("Benchmark", "Completed benchmarking with ${expectedTokens} token prompt")
                Log.i("Benchmark", "Result for $expectedTokens tokens: $result")
            }

            Log.i("Benchmark", "All benchmark runs completed")
            results
        }

        suspend fun runBenchmarkInferencePerPrompt(onEach: suspend (Map<String, Any>) -> Unit) = withContext(Dispatchers.IO) {
            val ctx = this@AppViewModel.application

            val prompts = listOf(
                Pair(_32_PROMPT, 32),
                Pair(_64_PROMPT, 64),
                Pair(_128_PROMPT, 128)
            )

            for ((prompt, expectedTokens) in prompts) {
                Log.i("Benchmark", "Start benchmarking with ${expectedTokens} token prompt")

                val startBatteryTemp = TemperatureUtils.getBatteryTemperature(ctx)
                val startThermals = TemperatureUtils.getThermalInfo()
                val startUsedMemMb = MemoryInfo.getVmRssMb()
                val startBatteryStats = BatteryInfo.getPreciseBatteryStats(ctx)
                val startBatteryLevel = DeviceInfoUtils.getBatteryLevel(ctx)
                val totalMemoryBytes = DeviceInfoUtils.getTotalMemory(ctx)
                val totalMemoryMb = totalMemoryBytes / 1024.0 / 1024.0

                val ramSpikes = mutableListOf<Long>()
                val samplerJob = CoroutineScope(Dispatchers.Default).launch {
                    while (isActive) {
                        try {
                            val used = MemoryInfo.getVmRssMb()
                            ramSpikes.add(used)
                        } catch (_: Throwable) {
                        }
                        delay(2000L)
                    }
                }

                val startNs = System.nanoTime()
                val inputTokens = expectedTokens
                var firstTokenMs: Long? = null
                val outputTokens = 50
                var outputTokens2 = 0

                try {
                    val responses = engine.chat.completions.create(
                        messages = listOf(
                            OpenAIProtocol.ChatCompletionMessage(
                                role = OpenAIProtocol.ChatCompletionRole.user,
                                content = OpenAIProtocol.ChatCompletionMessageContent(text = prompt)
                            )
                        ),
                        max_tokens = outputTokens,
                        stream = true,
                        stream_options = OpenAIProtocol.StreamOptions(include_usage = true),
                    )
                    var output = ""
                    for (res in responses) {
                        for (choice in res.choices) {
                            choice.delta.content?.let { content ->
                                val nowNs = System.nanoTime()
                                if (firstTokenMs == null) {
                                    firstTokenMs = (nowNs - startNs) / 1_000_000
                                }
                                output += content.asText()
                                outputTokens2 += 1
                            }
                        }
                    }
                } finally {
                    samplerJob.cancel()
                }

                val stopNs = System.nanoTime()
                val totalMs = (System.nanoTime() - startNs) / 1_000_000
                val endBatteryTemp = TemperatureUtils.getBatteryTemperature(ctx)
                val endThermals = TemperatureUtils.getThermalInfo()
                val endUsedMemMb = MemoryInfo.getVmRssMb()
                val endBatteryStats = BatteryInfo.getPreciseBatteryStats(ctx)
                val endBatteryLevel = DeviceInfoUtils.getBatteryLevel(ctx)

                val ttftMs = (firstTokenMs ?: totalMs)
                val inferenceTimeMs = totalMs
                val tokensPerSecond = if (inferenceTimeMs > 0) {
                    outputTokens.toDouble() / (inferenceTimeMs.toDouble() / 1000.0)
                } else 0.0

                val result = mutableMapOf<String, Any>()
                result["stopMs"] = stopNs / 1_000_000
                result["startMs"] = startNs / 1_000_000
                result["tps"] = tokensPerSecond
                result["ttft"] = ttftMs
                result["inferenceTime"] = inferenceTimeMs
                result["outputTokens"] = outputTokens
                result["outputTokens2"] = outputTokens2
                result["inputTokens"] = inputTokens

                val ramSeries = ArrayList<Long>(2 + ramSpikes.size)
                ramSeries.add(startUsedMemMb)
                ramSeries.addAll(ramSpikes)
                ramSeries.add(endUsedMemMb)
                result["ram"] = ramSeries

                val battTempSeries = listOf(
                    startBatteryTemp ?: 0f,
                    endBatteryTemp ?: 0f
                )
                result["batteryTempreture"] = battTempSeries

                result["sensorTempreratures"] = listOf(startThermals, endThermals)
                result["battery"] = listOf(startBatteryLevel, endBatteryLevel)
                result["batteryInfos"] = listOf(startBatteryStats, endBatteryStats)

                val startRamUsagePct = if (totalMemoryMb > 0) (startUsedMemMb / totalMemoryMb) * 100.0 else 0.0
                val endRamUsagePct = if (totalMemoryMb > 0) (endUsedMemMb / totalMemoryMb) * 100.0 else 0.0
                result["startRamUsagePct"] = startRamUsagePct
                result["endRamUsagePct"] = endRamUsagePct

                Log.i("Benchmark","Hello")
                Log.i("Benchmark", "Completed benchmarking with ${expectedTokens} token prompt")
                Log.i("Benchmark", "Result for $expectedTokens tokens: $result")

                onEach(result)
            }
        }
    }

    fun runBenchmark(modelState: ModelState, userId: String, model: String) {
        val family = modelState.getModelFamily()
        viewModelScope.launch(Dispatchers.Main) {
            chatState.isBenchmarking.value = true
            chatState.benchmarkRunTotal.value = 3
            chatState.benchmarkRunCount.value = 0
            chatState.modelName.value = modelState.modelConfig.modelId
            
            try {
                if (modelState.modelInitState.value != ModelInitState.Finished) {
                    chatState.report.value = "Benchmark: downloading model..."
                    if (modelState.modelInitState.value == ModelInitState.Paused || modelState.modelInitState.value == ModelInitState.Initializing) {
                        modelState.handleStart()
                    }
                    while (modelState.modelInitState.value != ModelInitState.Finished) {
                        if (modelState.modelInitState.value == ModelInitState.Paused) {
                            modelState.handleStart()
                        }
                        delay(1000)
                    }
                }

                for (i in 1..3) {
                    chatState.report.value = "Benchmark ${i}/${chatState.benchmarkRunTotal.value}: initializing..."
                    val initInfo = benchmarkInitialize(modelState)

                    chatState.report.value = "Benchmark ${i}/${chatState.benchmarkRunTotal.value}: running inference..."
                    chatState.runBenchmarkInferencePerPrompt { singleRun ->
                        chatState.report.value = "Benchmark ${i}/${chatState.benchmarkRunTotal.value}: posting results..."
                        runBlocking {
                            withTimeoutOrNull(5000L) { // Limit czasu: 5 sekund (5000 milisekund)
                                benchmarkPostResults(
                                    modelState.modelConfig,
                                    initInfo,
                                    listOf(singleRun),
                                    userId,
                                    model,
                                    family,
                                    UUID.randomUUID().toString()
                                )
                            }
                        }
                    }

                    // Not calling benchmarkRelease() here: it fires an async, unawaited
                    // engine.unload(), and the next iteration's benchmarkInitialize() ->
                    // startChat() -> requestReloadChat() already does its own unload()
                    // followed by reload() before that run starts. The redundant unload
                    // could race with the next run's reload, which is what was causing
                    // "Background engine is not loaded" crashes between runs.
                    //
                    // (Tried replacing the per-run reload with a lightweight engine.reset()
                    // to keep the model loaded and avoid the RAM growth from repeated full
                    // reloads — reverted after it caused an ANR/crash at the run 1->2
                    // transition, likely because native reset() has no completion signal
                    // and the fixed delay used to approximate one wasn't long enough on
                    // this device. resetEngine()/benchmarkReset() are left in place below
                    // in case this is worth revisiting with a longer delay or a real
                    // completion check.)
                    chatState.benchmarkRunCount.value = i
                }

                // Skipping modelState.handleClear() here: it calls engine.unload() (native)
                // followed by clear()'s file-deletion require() checks, which was crashing
                // the app after the final run. Results are already posted by this point, so
                // this only affects whether the model's downloaded files get cleaned up —
                // they'll now stay on device instead.
                chatState.report.value = "Benchmark: done ${chatState.benchmarkRunCount.value}/${chatState.benchmarkRunTotal.value}"
            } catch (e: Exception) {
                chatState.report.value = "Benchmark failed: ${e.localizedMessage}"
            } finally {
                chatState.isBenchmarking.value = false
            }
        }
    }

    private data class BenchmarkInitInfo(
        val modelLib: String,
        val loadTimeMs: Long
    )

    private suspend fun benchmarkInitialize(modelState: ModelState): BenchmarkInitInfo =
        withContext(Dispatchers.Main) {
            val startNs = System.nanoTime()
            modelState.startChat()
            withTimeout(60_000L) {
                while (!chatState.chatable()) {
                    delay(10L)
                }
            }
            val loadTimeMs = (System.nanoTime() - startNs) / 1_000_000
            BenchmarkInitInfo(
                modelLib = modelState.modelConfig.modelLib,
                loadTimeMs = loadTimeMs
            )
        }

    // Used for runs after the first: clears request/KV-cache state via engine.reset()
    // instead of a full unload+reload, so the model stays loaded (faster, and avoids the
    // RAM growth we saw across repeated full reloads). See resetEngine()'s comment for why
    // the short delay is here: native reset() has no completion signal to await.
    private suspend fun benchmarkReset(modelState: ModelState): BenchmarkInitInfo =
        withContext(Dispatchers.Main) {
            val startNs = System.nanoTime()
            chatState.resetEngine()
            delay(300L)
            val resetTimeMs = (System.nanoTime() - startNs) / 1_000_000
            BenchmarkInitInfo(
                modelLib = modelState.modelConfig.modelLib,
                loadTimeMs = resetTimeMs
            )
        }

    private suspend fun benchmarkRunInference(): List<Map<String, Any>> = withContext(Dispatchers.Main) {
        chatState.runBenchmarkInference()
    }

    private suspend fun benchmarkPostResults(
        modelConfig: ModelConfig,
        initInfo: BenchmarkInitInfo,
        inferenceResults: List<Map<String, Any>>,
        userId: String,
        model: String,
        family: String,
        runId: String
    ) = withContext(Dispatchers.IO) {
        try {
            val loadTime = initInfo.loadTimeMs

            val dataObject = mapOf(
                "loadTime" to loadTime,
                "runs" to inferenceResults
            )

            val phoneData = mapOf(
                "model" to DeviceInfoUtils.getModel(),
                "brand" to DeviceInfoUtils.getBrand(),
                "systemName" to DeviceInfoUtils.getSystemName(),
                "systemVersion" to DeviceInfoUtils.getSystemVersion(),
                "totalMemory" to DeviceInfoUtils.getTotalMemory(application)
            )

            val payload = mapOf(
                "userId" to userId,
                "model" to model,
                "family" to family,
                "data" to gson.toJson(dataObject),
                "phoneData" to gson.toJson(phoneData),
                "version" to Config.VERSION,
                "runId" to runId
            )

            val json = gson.toJson(payload)
            val url = URL("${Config.API_URL}/saveBenchmark")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-PROTECT-KEY", Config.API_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                throw RuntimeException("POST failed: ${'$'}code ${'$'}err")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
            Log.i("Benchmark", "Posted benchmark results with ${inferenceResults.size} runs")
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                issueAlert("Benchmark post failed: ${'$'}{e.localizedMessage}")
            }
        }
    }

    private suspend fun benchmarkRelease() = withContext(Dispatchers.Main) {
        chatState.requestTerminateChat { }
    }
}

enum class ModelInitState {
    Initializing,
    Indexing,
    Paused,
    Downloading,
    Pausing,
    Clearing,
    Deleting,
    Finished
}

enum class ModelChatState {
    Generating,
    Resetting,
    Reloading,
    Terminating,
    Ready,
    Falied
}

enum class MessageRole {
    Assistant,
    User
}

data class DownloadTask(val url: URL, val file: File)

data class MessageData(val role: MessageRole, val text: String, val id: UUID = UUID.randomUUID(), var imageUri: Uri? = null)

data class AppConfig(
    @SerializedName("model_libs") var modelLibs: MutableList<String>,
    @SerializedName("model_list") val modelList: MutableList<ModelRecord>,
)

data class ModelRecord(
    @SerializedName("model_url") val modelUrl: String,
    @SerializedName("model_id") val modelId: String,
    @SerializedName("estimated_vram_bytes") val estimatedVramBytes: Long?,
    @SerializedName("model_lib") val modelLib: String
)

data class ModelConfig(
    @SerializedName("model_lib") var modelLib: String,
    @SerializedName("model_id") var modelId: String,
    @SerializedName("estimated_vram_bytes") var estimatedVramBytes: Long?,
    @SerializedName("tokenizer_files") val tokenizerFiles: List<String>,
    @SerializedName("context_window_size") val contextWindowSize: Int,
    @SerializedName("prefill_chunk_size") val prefillChunkSize: Int,
)

data class ParamsRecord(
    @SerializedName("dataPath") val dataPath: String
)

data class ParamsConfig(
    @SerializedName("records") val paramsRecords: List<ParamsRecord>
)