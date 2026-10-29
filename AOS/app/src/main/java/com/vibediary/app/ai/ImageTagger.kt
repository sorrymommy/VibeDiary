package com.vibediary.app.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** On-device photo labelling (ML Kit bundled model, no network, no cost). */
object ImageTagger {
    suspend fun tags(context: Context, photo: File): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, Uri.fromFile(photo))
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder().setConfidenceThreshold(0.7f).build()
            )
            try {
                labeler.process(image).await().mapNotNull { KO[it.text] }.distinct().take(3)
            } finally {
                labeler.close()
            }
        }.getOrDefault(emptyList())
    }

    /** ML Kit returns English labels; keep only the ones that make useful diary tags. */
    private val KO = mapOf(
        "Food" to "음식", "Cuisine" to "음식", "Dish" to "음식", "Bento" to "음식", "Pizza" to "음식",
        "Cake" to "디저트", "Dessert" to "디저트", "Cookie" to "디저트", "Bread" to "빵",
        "Coffee" to "커피", "Cup" to "카페", "Drink" to "음료", "Wine" to "술", "Beer" to "술",
        "Sky" to "하늘", "Cloud" to "하늘", "Sunset" to "노을", "Moon" to "밤하늘",
        "Flower" to "꽃", "Plant" to "식물", "Tree" to "나무", "Forest" to "숲", "Garden" to "정원",
        "Mountain" to "산", "Lake" to "호수", "River" to "강", "Sea" to "바다", "Beach" to "바다", "Waterfall" to "폭포",
        "Snow" to "눈", "Park" to "공원", "Bridge" to "다리", "Building" to "건물", "Skyscraper" to "도시",
        "Dog" to "강아지", "Cat" to "고양이", "Bird" to "새", "Pet" to "반려동물",
        "Selfie" to "셀카", "Smile" to "미소", "Baby" to "아기", "Team" to "모임", "Party" to "파티",
        "Car" to "자동차", "Bicycle" to "자전거", "Airplane" to "여행", "Train" to "기차",
        "Book" to "책", "Musical instrument" to "음악", "Concert" to "공연", "Stadium" to "경기장",
        "Christmas" to "크리스마스", "Fireworks" to "불꽃놀이", "Night" to "밤",
    )
}
