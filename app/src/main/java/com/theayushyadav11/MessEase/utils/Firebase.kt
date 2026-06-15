package com.theayushyadav11.MessEase.utils

import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.messaging.FirebaseMessaging
import com.theayushyadav11.MessEase.Models.DayMenu
import com.theayushyadav11.MessEase.Models.Menu
import com.theayushyadav11.MessEase.Models.Particulars
import com.theayushyadav11.MessEase.Models.User
import com.theayushyadav11.MessEase.utils.Constants.Companion.COORDINATOR
import com.theayushyadav11.MessEase.utils.Constants.Companion.DEVELOPER
import com.theayushyadav11.MessEase.utils.Constants.Companion.MAIN_MENU
import com.theayushyadav11.MessEase.utils.Constants.Companion.MEMBER
import com.theayushyadav11.MessEase.utils.Constants.Companion.MENU
import com.theayushyadav11.MessEase.utils.Constants.Companion.SENIOR_MEMBER
import com.theayushyadav11.MessEase.utils.Constants.Companion.TAG
import com.theayushyadav11.MessEase.utils.Constants.Companion.UPDATE
import com.theayushyadav11.MessEase.utils.Constants.Companion.USERS
import com.theayushyadav11.MessEase.utils.Constants.Companion.UID
import com.theayushyadav11.MessEase.utils.Constants.Companion.VOLUNTEER
import com.theayushyadav11.MessEase.utils.Constants.Companion.auth
import com.theayushyadav11.MessEase.utils.Constants.Companion.firestoreReference
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

class FireBase {
    fun getUser(uid: String, onSuccess: (User) -> Unit, onFailure: (Exception) -> Unit) {
        firestoreReference.collection(USERS).whereEqualTo(UID, uid)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    onFailure(error)
                    return@addSnapshotListener
                }
                val doc = value?.documents?.get(0)
                if (doc != null) {
                    onSuccess(doc.toObject(User::class.java)!!)
                }
            }
    }

    fun uploadFile(
        uri: Uri,
        path: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        MediaManager.get().upload(uri)
            .unsigned("preset1")
            .option("folder", "messease_uploads/$path")
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                    val url = resultData?.get("secure_url").toString()
                    onSuccess(url)
                }

                override fun onError(requestId: String?, error: ErrorInfo?) {
                    onFailure(Exception(error?.description ?: "Upload failed"))
                }

                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            })
            .dispatch()
    }

    fun uploadphoto(
        uri: ByteArray,
        path: String,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        try {
            val tempFile = File.createTempFile("upload_", ".jpg")
            tempFile.writeBytes(uri)

            MediaManager.get().upload(tempFile.absolutePath)
                .unsigned("preset1")
                .option("folder", "messease_uploads/$path")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String?) {}
                    override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                    override fun onSuccess(requestId: String?, resultData: Map<*, *>?) {
                        val url = resultData?.get("secure_url").toString()
                        tempFile.delete()
                        onSuccess(url)
                    }

                    override fun onError(requestId: String?, error: ErrorInfo?) {
                        tempFile.delete()
                        onFailure(Exception(error?.description ?: "Upload failed"))
                    }

                    override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
                })
                .dispatch()
        } catch (e: Exception) {
            onFailure(e)
        }
    }

    fun deletefile(url: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        // For Cloudinary, deletion via the client SDK requires signed requests.
        // Using the Admin API via OkHttp with API credentials.
        // NOTE: For production, this should be done via your backend server.
        try {
            val publicId = extractPublicIdFromUrl(url)
            if (publicId == null) {
                onSuccess() // URL not from Cloudinary or invalid, treat as already deleted
                return
            }

            val client = OkHttpClient()
            val apiKey = "YOUR_API_KEY"       // TODO: Replace with your Cloudinary API key
            val apiSecret = "YOUR_API_SECRET" // TODO: Replace with your Cloudinary API secret
            val cloudName = "dw6gpswrw"
            val timestamp = (System.currentTimeMillis() / 1000).toString()

            // Generate signature: sha1("public_id=<publicId>&timestamp=<timestamp><apiSecret>")
            val toSign = "public_id=$publicId&timestamp=$timestamp$apiSecret"
            val md = java.security.MessageDigest.getInstance("SHA-1")
            val signature = md.digest(toSign.toByteArray()).joinToString("") { "%02x".format(it) }

            val requestBody = FormBody.Builder()
                .add("public_id", publicId)
                .add("timestamp", timestamp)
                .add("api_key", apiKey)
                .add("signature", signature)
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$cloudName/image/destroy")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    onFailure(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        onSuccess()
                    } else {
                        onFailure(Exception("Delete failed: ${response.code}"))
                    }
                }
            })
        } catch (e: Exception) {
            if (e.message?.contains("not found") == true) {
                onSuccess()
            } else {
                onFailure(e)
            }
        }
    }

    /**
     * Extracts the public_id from a Cloudinary URL.
     * Example URL: https://res.cloudinary.com/dw6gpswrw/image/upload/v1234/messease_uploads/MsgImages/abc123.jpg
     * Returns: messease_uploads/MsgImages/abc123
     */
    private fun extractPublicIdFromUrl(url: String): String? {
        try {
            val uri = java.net.URI(url)
            val path = uri.path // e.g., /dw6gpswrw/image/upload/v1234/folder/file.jpg
            val parts = path.split("/upload/")
            if (parts.size < 2) return null
            var afterUpload = parts[1]
            // Remove version prefix (e.g., v1234567890/)
            if (afterUpload.startsWith("v") && afterUpload.contains("/")) {
                afterUpload = afterUpload.substringAfter("/")
            }
            // Remove file extension
            return afterUpload.substringBeforeLast(".")
        } catch (e: Exception) {
            return null
        }
    }

    fun getIcon(designation: String): Int {
        when (designation) {
            COORDINATOR -> return com.theayushyadav11.MessEase.R.drawable.coordinator
            MEMBER -> return com.theayushyadav11.MessEase.R.drawable.member
            VOLUNTEER -> return com.theayushyadav11.MessEase.R.drawable.volunteer
            SENIOR_MEMBER -> return com.theayushyadav11.MessEase.R.drawable.seniormember
            DEVELOPER -> return com.theayushyadav11.MessEase.R.drawable.developer
            else ->
                return com.theayushyadav11.MessEase.R.drawable.logo
        }
    }

    fun getToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                firestoreReference.collection("Users").document(auth.currentUser?.uid.toString())
                    .update("token", token)

            }
        }
    }

    fun deleteSubcollections(documentRef: DocumentReference, collection: String): Task<Void> {
        return documentRef.collection(collection).get()
            .continueWithTask { task ->
                val batch = firestoreReference.batch()
                val snapshot = task.result
                for (doc in snapshot.documents) {
                    Log.d(TAG, "Deleting ${doc.id}")
                    batch.delete(doc.reference)
                    deleteSubcollections(
                        doc.reference,
                        collection
                    ) // Recursively delete nested subcollections
                }
                batch.commit()
            }
    }

    fun getMainMenu(onResult: (Menu) -> Unit) {


        firestoreReference.collection("MainMenu").document("menu").addSnapshotListener { value, error ->
            if(error!=null)
            {
                onResult(Menu())
                return@addSnapshotListener
            }
            onResult(value?.toObject(Menu::class.java)!!)
        }
    }

    fun getUpdates(onResult: (String, String) -> Unit) {
        firestoreReference.collection("Update").document("update").get().addOnSuccessListener {
            val version = it.getString("version")
            val url = it.getString("url")
            if (version != null && url != null) {
                onResult(version, url)
            } else {

                onResult("", "")
            }

        }
            .addOnFailureListener {
                onResult("", "")
            }
    }

    fun getSenderDeatails(onResult: (String, String, String) -> Unit) {
        firestoreReference.collection("Sender").document("sender").get().addOnSuccessListener {
            val toEmail = it.getString("toEmail")
            val email = it.getString("email")
            val password = it.getString("password")
            if (toEmail != null && email != null && password != null) {
                onResult(email, password, toEmail)
            } else {
                onResult(
                    "theayushyadav11@gmail.com",
                    "hagd snwa yvpn vwwf",
                    "theayushyadav11b@gmail.com"
                )
            }

        }
            .addOnFailureListener {
                onResult(
                    "theayushyadav11@gmail.com",
                    "hagd snwa yvpn vwwf",
                    "theayushyadav11b@gmail.com"
                )
            }
    }
    fun runScripts(user: User) {
        addMenu(user)
    }

    private fun addMenu(user: User) {
        val menu = Menu(
            id = 0,
            comp = "",
            creator = user,
            menu = listOf(
                DayMenu(),
                DayMenu(
                    listOf(
                        Particulars(
                            type = "Breakfast",
                            food = " Spicy Matar Chhole,\n" +
                                    " Kulcha, Milk, Tea,\n" +
                                    " Banana/egg,Bread,\n" +
                                    " butter/jam ",
                            time = "8:30 AM to 10:00 AM"
                        ),
                        Particulars(
                            type = "Lunch",
                            food = "  Vegetable Biryani ,Dal\n" +
                                    " Makhni, Tawa Paratha,\n" +
                                    " Raita",
                            time = "12:30 PM to 2:30 PM"
                        ),
                        Particulars(
                            type = "Snacks",
                            food = " Bhel, tea",
                            time = "5:00 PM to 6:00 PM"
                        ),

                        Particulars(
                            type = "Dinner",
                            food = "Sewai ,Jeera\n" +
                                    " Rice, Roti,Malai\n" +
                                    " Kofta ,Punjabi\n" +
                                    " Dal Tadka ",
                            time = "7:30 PM to 9:30 PM"
                        )
                    )

                ),
                DayMenu(
                    listOf(
                        Particulars(
                            type = "Breakfast",
                            food = " Pav Bhaji ,Daliya,Milk,\n" +
                                    " Tea ,Banana /Egg -1pc,\n" +
                                    " Bread Butter/jam ",
                            time = "8:30 AM to 10:00 AM"
                        ),
                        Particulars(
                            type = "Lunch",
                            food = "  Spicy Chhola,\n" +
                                    " Poori,\n" +
                                    " Curd,\n" +
                                    " Jeera Rice",
                            time = "12:30 PM to 2:30 PM"
                        ),
                        Particulars(
                            type = "Snacks",
                            food = "  Maggi, Tea",
                            time = "5:00 PM to 6:00 PM"
                        ),
                        Particulars(
                            type = "Dinner",
                            food = "Arahar Dal Fry,\n" +
                                    " Baigan Bharta,\n" +
                                    " Roti,\n" +
                                    " Rice ",
                            time = "7:30 PM to 9:30 PM"
                        )
                    )

                ),
                DayMenu(
                    listOf(
                        Particulars(
                            type = "Breakfast",
                            food = " Medu vada,\n" +
                                    " Coconut chutney,\n" +
                                    " sambhar,\n" +
                                    " milk,\n" +
                                    " tea,\n" +
                                    " Banana/egg,\n" +
                                    " Bread, butter/jam",
                            time = "8:30 AM to 10:00 AM"
                        ),
                        Particulars(
                            type = "Lunch",
                            food = " Rajma Masala,\n" +
                                    " Boondi Raita,\n" +
                                    " Roti,\n" +
                                    " Jeera Rice ",
                            time = "12:30 PM to 2:30 PM"
                        ),
                        Particulars(
                            type = "Snacks",
                            food = "Bread Pakoda,\n" +
                                    " Sauce,Chutney ,\n" +
                                    " Tea ",
                            time = "5:00 PM to 6:00 PM"
                        ),
                        Particulars(
                            type = "Dinner",
                            food = " Lauki Kofta,\n" +
                                    " vegetable tahri roti raita\n" +
                                    " thick ",
                            time = "7:30 PM to 9:30 PM"
                        )
                    )

                ),
                DayMenu(
                    listOf(
                        Particulars(
                            type = "Breakfast",
                            food = "Aloo paratha,\n" +
                                    " curd, milk, tea,\n" +
                                    " Banana/egg,\n" +
                                    " Bread, Butter/jam",
                            time = "8:30 AM to 10:00 AM"
                        ),
                        Particulars(
                            type = "Lunch",
                            food = "Sambhar,\n" +
                                    " Roti,\n" +
                                    " Rice ,\n" +
                                    " Aaloo Gobi",
                            time = "12:30 PM to 2:30 PM"
                        ),
                        Particulars(
                            type = "Snacks",
                            food = "Pyaz Bhajiya, chutney,Tea",
                            time = "5:00 PM to 6:00 PM"
                        ),
                        Particulars(
                            type = "Dinner",
                            food = " Shahi Paneer, Zeera rice,\n" +
                                    " Mix Dal fry, Jalebi/\n" +
                                    " Moong daal halwa ",
                            time = "7:30 PM to 9:30 PM"
                        )
                    )

                ),
                DayMenu(
                    listOf(
                        Particulars(
                            type = "Breakfast",
                            food = " Aloo Puri, Banana/\n" +
                                    " egg,\n" +
                                    " Milk,Tea,\n" +
                                    " Bread,Butter/Jam",
                            time = "8:30 AM to 10:00 AM"
                        ),
                        Particulars(
                            type = "Lunch",
                            food = "  Aaloo Pyaz Bhujia,Kadhi\n" +
                                    " pyaaz\n" +
                                    " pakoda,\n" +
                                    " rice ,roti",
                            time = "12:30 PM to 2:30 PM"
                        ),
                        Particulars(
                            type = "Snacks",
                            food = " Chola Samosa,Tea",
                            time = "5:00 PM to 6:00 PM"
                        ),
                        Particulars(
                            type = "Dinner",
                            food = " Aloo matar gajar,\n" +
                                    " Rice,Roti,\n" +
                                    " Dal palak",
                            time = "7:30 PM to 9:30 PM"
                        )
                    )

                ),
                DayMenu(
                    listOf(
                        Particulars(
                            type = "Breakfast",
                            food = " Gobi Paratha,\n" +
                                    " Banana/egg 1 pc\n" +
                                    " bread\n" +
                                    " Butter jam\n" +
                                    " Milk,Tea, ",
                            time = "8:30 AM to 10:00 AM"
                        ),
                        Particulars(
                            type = "Lunch",
                            food = "  Aloo Palak,\n" +
                                    " Arahar dal Fry,\n" +
                                    " Roti,Rice",
                            time = "12:30 PM to 2:30 PM"
                        ),
                        Particulars(
                            type = "Snacks",
                            food = " Aloo tikki matar chaat\n" +
                                    " with dahi and chutney",
                            time = "5:00 PM to 6:00 PM"
                        ),
                        Particulars(
                            type = "Dinner",
                            food = " Bhandara style sabji,\n" +
                                    " Poori , Black Masoor Dal,\n" +
                                    " Rice",
                            time = "7:30 PM to 9:30 PM"
                        )
                    )

                ),
                DayMenu(
                    listOf(
                        Particulars(
                            type = "Breakfast",
                            food = " Utappam, sambhar,\n" +
                                    " coconut chutney,\n" +
                                    " Milk, tea,\n" +
                                    " banana/egg, Bread,\n" +
                                    " butter/jam",
                            time = "8:30 AM to 10:00 AM"
                        ),
                        Particulars(
                            type = "Lunch",
                            food = " Pindi Choley\n" +
                                    " Bathure\n" +
                                    " Rice\n" +
                                    " Boondi Raita",
                            time = "12:30 PM to 2:30 PM"
                        ),
                        Particulars(
                            type = "Snacks",
                            food = " Rusk(5 pcs) ,Tea",
                            time = "5:00 PM to 6:00 PM"
                        ),
                        Particulars(
                            type = "Dinner",
                            food = "Veg\n" +
                                    " Jalfrezi,Arhar\n" +
                                    " Dal Fry\n" +
                                    " Roti, JeeraRice",
                            time = "7:30 PM to 9:30 PM"
                        )
                    )

                ),
            )

        )
        firestoreReference.collection(MAIN_MENU).document(MENU).set(menu)
        firestoreReference.collection(UPDATE).document("update").set(
            mapOf(
                "version" to "1.2",
                "url" to "https://github.com/iiitl/MessEase/releases/tag/release"
            )
        )
    }
}
