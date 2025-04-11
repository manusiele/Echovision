package com.example.echovision

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WelcomeScreen()
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(60.dp)
            .width(160.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF2196F3), Color(0xFF1976D2)),
                        start = Offset(0f, 0f),
                        end = Offset(100f, 100f)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@Composable
fun WelcomeScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Logo
            Image(
                painter = painterResource(id = R.drawable.logo2),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 220.dp)
                    .aspectRatio(
                        ratio = 16f/9f,
                        matchHeightConstraintsFirst = false
                    ),
                contentScale = ContentScale.FillWidth
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "Small Logo",
                    modifier = Modifier
                        .width(60.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "EchoVision",
                    color = Color.White,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Description Text
            Text(
                text = "Smarter Assistance for a Better Tomorrow!",
                color = Color.White,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Secondary Description
            Text(
                text = "Welcome! This app is designed to assist with accessibility, making navigation and communication easier. Use voice commands, gestures, and touch for a seamless experience tailored to your needs.",
                color = Color.Gray,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )

            // Get Started Button
            GradientButton(
                text = "Get Started",
                onClick = { /* Handle button click */ },
                modifier = Modifier.padding(bottom = 150.dp)
            )
        }
    }
}

@Composable
fun WelcomeScreenPreview() {
    WelcomeScreen()
}