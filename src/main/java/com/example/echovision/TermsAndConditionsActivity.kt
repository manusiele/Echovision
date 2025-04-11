package com.example.echovision

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.accessibility.AccessibilityEventCompat

@Composable
fun TermsAndConditionsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isLoading = remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            HeaderSection()
            Spacer(modifier = Modifier.height(25.dp))
            ContentSection()
            Spacer(modifier = Modifier.height(32.dp))
            ButtonsSection(
                isLoading = isLoading.value,
                onAgree = {
                    isLoading.value = true
                    try {
                        Toast.makeText(context, "Terms Accepted", Toast.LENGTH_SHORT).show()
                        context.startActivity(
                            Intent(context, DisabilitySelectionActivity::class.java)
                                .addFlags(AccessibilityEventCompat.TYPE_VIEW_TARGETED_BY_SCROLL)
                        )
                        (context as? ComponentActivity)?.finish()
                    } catch (e: Exception) {
                        Toast.makeText(context, "An unexpected error occurred", Toast.LENGTH_SHORT).show()
                    } finally {
                        isLoading.value = false
                    }
                },
                onDisagree = {
                    Toast.makeText(context, "You must accept the terms to continue", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun HeaderSection() {
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        Image(
            painter = painterResource(id = R.drawable.terms),
            contentDescription = "Logo",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 75.dp)
                .aspectRatio(1.7777778f),
            contentScale = ContentScale.FillWidth
        )
    }
}

@Composable
private fun ContentSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.9f), RoundedCornerShape(20.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "A Kind Notice",
            modifier = Modifier.padding(bottom = 34.dp),
            color = Color(0xFF039BE5),
            fontSize = 38.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        TermsList()
    }
}

@Composable
private fun TermsList() {
    val terms = listOf(
        "You agree to use the app for lawful purposes and avoid any misuse, including tampering with its functionality.",
        "You are responsible for providing accurate, complete information and securing your account.",
        "While we aim to provide accurate features like voice activation, we do not guarantee their flawless performance in all environments.",
        "EchoVision is not responsible for the content or functionality of third-party services or websites linked in the app.",
        "You acknowledge that the app's accessibility features may require system permissions to function properly.",
        "You are expected to comply with all applicable laws and not engage in harmful activities while using the app."
    )

    terms.forEachIndexed { index, term ->
        Text(
            text = "${index + 1}. $term",
            modifier = Modifier.padding(vertical = 10.dp),
            color = Color.White,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun ButtonsSection(
    isLoading: Boolean,
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { if (!isLoading) onAgree() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                disabledContainerColor = Color(0xFF4CAF50).copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Agree", fontSize = 20.sp)
        }

        Button(
            onClick = { if (!isLoading) onDisagree() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(vertical = 8.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE53935),
                disabledContainerColor = Color(0xFFE53935).copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Disagree", fontSize = 20.sp)
        }
    }
}