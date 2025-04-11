package com.example.echovision

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.echovision.Handicapped.HandicappedActivity
import com.example.echovision.hearingImpaired.HearingImpaired
import com.example.echovision.visualImpared.VisualImpairmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DisabilitySelectionScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Choose a Category",
            modifier = Modifier.padding(top = 70.dp),
            color = Color(0xFF00B0FF),
            fontSize = 40.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(50.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            horizontalAlignment = Alignment.Start
        ) {
            SectionBtn(
                image = R.drawable.blind1,
                text = "Support for Visual Impairment"
            ) {
                try {
                    Log.d("DisabilitySelection", "Creating Intent for VisualImpairmentActivity")
                    val intent = Intent(context, VisualImpairmentActivity::class.java)
                    Log.d("DisabilitySelection", "Starting VisualImpairmentActivity")
                    context.startActivity(intent)
                    Log.d("DisabilitySelection", "VisualImpairmentActivity started")
                } catch (e: Exception) {
                    Log.e("DisabilitySelection", "Error opening Visual Support", e)
                    Toast.makeText(context, "Error opening Visual Support", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

            SectionBtn(
                image = R.drawable.deaf1,
                text = "Assistance for the Deaf"
            ) {
                try {
                    context.startActivity(Intent(context, HearingImpaired::class.java))
                } catch (e: Exception) {
                    Toast.makeText(context, "Error opening Visual Support", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

            SectionBtn(
                image = R.drawable.handicapped,
                text = "Supporting People with Arm Disabilities"
            ) {
                try {
                    context.startActivity(Intent(context, HandicappedActivity::class.java))
                } catch (e: Exception) {
                    Toast.makeText(context, "Error opening Visual Support", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        NoticeCard()
    }
}

@Composable
fun SectionBtn(image: Int, text: String, onClick: () -> Unit) {
    val scale = remember { mutableStateOf(1f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(bottom = 5.dp)
            .scale(scale.value)
            .clickable {
                scale.value = 0.95f
                onClick()
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF414141)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(id = image),
                contentDescription = text,
                modifier = Modifier
                    .size(64.dp)
                    .padding(end = 16.dp)
            )

            Text(
                text = text,
                color = Color.White,
                fontSize = 20.sp
            )
        }
    }

    // Animation effect to return the button to its original size
    LaunchedEffect(scale.value) {
        if (scale.value < 1f) {
            delay(100)
            scale.value = 1f
        }
    }
}

@Composable
fun NoticeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Important Notice",
                color = Color(0xFF00B0FF),
                fontSize = 20.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "This application is designed to assist individuals with various disabilities. " +
                        "Please select the appropriate category based on your needs.",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun DisabilitySelectionPreview() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        DisabilitySelectionScreen()
    }
}