package com.theobuilder.app
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
@Composable fun TheoTheme(dark:Boolean,content:@Composable()->Unit){val c=LocalContext.current;val colors=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S){if(dark)dynamicDarkColorScheme(c)else dynamicLightColorScheme(c)}else if(dark)darkColorScheme()else lightColorScheme();MaterialTheme(colorScheme=colors,content=content)}
