package com.example

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GameApp()
            }
        }
    }
}

enum class Screen {
    Home, Wheel, Game, Score
}

@Composable
fun GameApp() {
    val view = LocalView.current
    
    // Core Game Screen State
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var currentWord by remember { mutableStateOf("") }
    var timeLeft by remember { mutableStateOf(50) } // Fast 50s timer for 1 single word
    var isPlaying by remember { mutableStateOf(false) }
    
    // Track results for current round: List of Pair(Word, GuessedCorrectly)
    val roundHistory = remember { mutableStateListOf<Pair<String, Boolean>>() }
    
    // Sessions Active Word Pool Map to ensure STRICT NO-REPEAT during session
    val activePools = remember { mutableStateMapOf<Int, MutableList<String>>() }
    
    // Players list and scores state
    val playersList = remember { mutableStateListOf<String>("كليوباترا", "توت عنخ آمون", "رمسيس") }
    val playerScores = remember { mutableStateMapOf<String, Int>("كليوباترا" to 0, "توت عنخ آمون" to 0, "رمسيس" to 0) }
    var currentPlayerIndex by remember { mutableStateOf(0) }
    
    // Track if score was credited for the current round to avoid double-adding
    var scoreCreditedForRound by remember { mutableStateOf(false) }

    // Initialize Pools at startup
    LaunchedEffect(Unit) {
        activePools.clear()
        GameData.categories.forEach { category ->
            activePools[category.id] = category.items.toMutableList()
        }
    }

    // Timer coroutine
    LaunchedEffect(isPlaying, timeLeft, currentScreen) {
        if (isPlaying && currentScreen == Screen.Game) {
            if (timeLeft > 0) {
                delay(1000)
                timeLeft -= 1
            } else {
                // Time's up! Word missed, end round immediately
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                SoundHelper.playTimeoutSound()
                roundHistory.clear()
                roundHistory.add(Pair(currentWord, false))
                isPlaying = false
                currentScreen = Screen.Score
            }
        }
    }

    // Process score once when entering results
    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.Score) {
            if (!scoreCreditedForRound) {
                val correctCount = roundHistory.count { it.second }
                val activePlayer = if (playersList.isNotEmpty()) playersList[currentPlayerIndex] else "لاعب"
                playerScores[activePlayer] = (playerScores[activePlayer] ?: 0) + correctCount
                scoreCreditedForRound = true
            }
        } else {
            scoreCreditedForRound = false
        }
    }

    // Helper to trigger haptic feedback
    fun triggerTapFeedback() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        SoundHelper.playClickSound()
    }

    fun triggerSuccessFeedback() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    // Core Word Pull Mechanics
    fun pullNextWord(category: Category) {
        val pool = activePools[category.id]
        if (pool.isNullOrEmpty()) {
            activePools[category.id] = category.items.toMutableList()
        }
        
        val currentPool = activePools[category.id]
        if (!currentPool.isNullOrEmpty()) {
            val randomIndex = (0 until currentPool.size).random()
            currentWord = currentPool[randomIndex]
            currentPool.removeAt(randomIndex) // Guarantee no repeats
        } else {
            currentWord = "انتهت الكلمات!"
        }
    }

    // Root UI container with Egyptian royal desert sand gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(BgCreamLight, BgCreamDark),
                    start = Offset(0f, 0f),
                    end = Offset(1000f, 2000f)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
            },
            label = "ScreenTransition"
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(
                    playersList = playersList,
                    onAddPlayer = { name ->
                        if (name.isNotBlank() && !playersList.contains(name)) {
                            playersList.add(name)
                            playerScores[name] = 0
                        }
                    },
                    onRemovePlayer = { name ->
                        playersList.remove(name)
                        playerScores.remove(name)
                        if (currentPlayerIndex >= playersList.size && playersList.isNotEmpty()) {
                            currentPlayerIndex = playersList.size - 1
                        } else if (playersList.isEmpty()) {
                            currentPlayerIndex = 0
                        }
                    },
                    onStartClick = {
                        triggerTapFeedback()
                        if (playersList.isEmpty()) {
                            playersList.add("اللاعب الملكي")
                            playerScores["اللاعب الملكي"] = 0
                        }
                        currentScreen = Screen.Wheel
                    }
                )
                
                Screen.Wheel -> WheelScreen(
                    activePools = activePools,
                    currentPlayerName = if (playersList.isNotEmpty()) playersList[currentPlayerIndex] else "اللاعب",
                    onCategorySelected = { category ->
                        selectedCategory = category
                    },
                    onStartGame = { category ->
                        triggerTapFeedback()
                        selectedCategory = category
                        roundHistory.clear()
                        timeLeft = 50 // 50 seconds for the single word
                        pullNextWord(category)
                        isPlaying = true
                        currentScreen = Screen.Game
                    },
                    onBack = {
                        triggerTapFeedback()
                        currentScreen = Screen.Home
                    }
                )
                
                Screen.Game -> GameplayScreen(
                    category = selectedCategory ?: GameData.categories.first(),
                    currentWord = currentWord,
                    timeLeft = timeLeft,
                    currentPlayerName = if (playersList.isNotEmpty()) playersList[currentPlayerIndex] else "اللاعب",
                    onCorrect = {
                        triggerSuccessFeedback()
                        SoundHelper.playCorrectSound()
                        roundHistory.clear()
                        roundHistory.add(Pair(currentWord, true)) // Word guessed correctly
                        isPlaying = false
                        currentScreen = Screen.Score
                    },
                    onSkip = {
                        triggerTapFeedback()
                        SoundHelper.playSkipSound()
                        roundHistory.clear()
                        roundHistory.add(Pair(currentWord, false)) // Word skipped
                        isPlaying = false
                        currentScreen = Screen.Score
                    },
                    onEndGame = {
                        triggerTapFeedback()
                        isPlaying = false
                        currentScreen = Screen.Score
                    }
                )
                
                Screen.Score -> ScoreScreen(
                    category = selectedCategory ?: GameData.categories.first(),
                    roundHistory = roundHistory,
                    currentPlayerName = if (playersList.isNotEmpty()) playersList[currentPlayerIndex] else "اللاعب",
                    playerScores = playerScores,
                    playersList = playersList,
                    onPlayAgain = {
                        triggerTapFeedback()
                        // Rotate to next player
                        if (playersList.isNotEmpty()) {
                            currentPlayerIndex = (currentPlayerIndex + 1) % playersList.size
                        }
                        currentScreen = Screen.Wheel
                    },
                    onHome = {
                        triggerTapFeedback()
                        currentScreen = Screen.Home
                    }
                )
            }
        }
    }
}

// ==========================================
// SCREEN 1: HOME SCREEN (EGYPTIAN RESPONSIVE)
// ==========================================
@Composable
fun HomeScreen(
    playersList: List<String>,
    onAddPlayer: (String) -> Unit,
    onRemovePlayer: (String) -> Unit,
    onStartClick: () -> Unit
) {
    var textVal by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Beautiful Egyptian Line-Art Pyramids and Eye of Horus logo
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .background(Color.White, RoundedCornerShape(32.dp))
                        .border(2.dp, AccentGold, RoundedCornerShape(32.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ElegantLogoCanvas()
                }
            }
            
            item {
                // Egyptian Title Content Area
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "لعبة الأسئلة والألغاز المصرية",
                        style = TextStyle(
                            color = AccentLapis,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "خمن أنا مين؟",
                        style = TextStyle(
                            color = TextDeepCoffee,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Elegant Pharaoh Gold Divider
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(AccentGold, AccentLapis, AccentGold)
                                ),
                                RoundedCornerShape(2.dp)
                            )
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Text(
                        text = "كل لاعب له كلمة واحدة فقط ليخمنها!\nتحدى أصدقائك في أسرع تخمين للفئات العشوائية المكتومة.",
                        style = TextStyle(
                            color = TextMediumBrown,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    )
                }
            }
            
            // Modern Egyptian Multi-player Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(1.dp, DividerWarm, RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "👑 قائمة الملوك والملكات",
                            style = TextStyle(
                                color = AccentLapis,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${playersList.size} لاعبين",
                            style = TextStyle(
                                color = TextLightBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textVal,
                            onValueChange = { textVal = it },
                            placeholder = { Text("اكتب اسم اللاعب هنا...", color = TextLightBrown.copy(alpha = 0.5f), fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentGold,
                                unfocusedBorderColor = DividerWarm,
                                focusedTextColor = TextDeepCoffee,
                                unfocusedTextColor = TextDeepCoffee,
                                focusedContainerColor = BgCreamLight,
                                unfocusedContainerColor = BgCreamLight
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = {
                                if (textVal.trim().isNotBlank()) {
                                    onAddPlayer(textVal.trim())
                                    textVal = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentLapis),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة", tint = Color.White)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    if (playersList.isEmpty()) {
                        Text(
                            text = "أضف أسماء اللاعبين للبدء بالقرعة الفرعونية!",
                            style = TextStyle(
                                color = TextLightBrown,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            playersList.forEach { player ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BgCreamLight)
                                        .border(1.dp, DividerWarm, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onRemovePlayer(player) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = AccentClay,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = player,
                                            style = TextStyle(
                                                color = TextDeepCoffee,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(text = "🏺", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            item {
                // Large Solid Egyptian Launch Button with gold outline
                val infiniteTransition = rememberInfiniteTransition(label = "ButtonPulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.03f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(AccentLapis, AccentTurquoise)
                            )
                        )
                        .border(2.dp, AccentGold, RoundedCornerShape(30.dp))
                        .clickable { onStartClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ادخل معبد التخمين 🏛️",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ==========================================
// CANVAS: PREMIUM EGYPTIAN PYRAMIDS LOGO
// ==========================================
@Composable
fun ElegantLogoCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "LogoPulse")
    val sunPulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sun"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2.2f

        // Outer Egyptian Ring with Lapis Blue and Gold dots
        drawCircle(
            color = AccentLapis,
            radius = radius + 4.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        drawCircle(
            color = AccentGold,
            radius = radius - 2.dp.toPx(),
            center = center,
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Draw Pyramids Outline
        val path = Path().apply {
            // Main Center Pyramid
            moveTo(center.x, center.y - 15.dp.toPx())
            lineTo(center.x - 28.dp.toPx(), center.y + 25.dp.toPx())
            lineTo(center.x + 28.dp.toPx(), center.y + 25.dp.toPx())
            close()
            
            // Left Small Pyramid
            moveTo(center.x - 18.dp.toPx(), center.y + 2.dp.toPx())
            lineTo(center.x - 42.dp.toPx(), center.y + 25.dp.toPx())
            lineTo(center.x + 2.dp.toPx(), center.y + 25.dp.toPx())
            close()
            
            // Right Small Pyramid
            moveTo(center.x + 18.dp.toPx(), center.y + 2.dp.toPx())
            lineTo(center.x - 2.dp.toPx(), center.y + 25.dp.toPx())
            lineTo(center.x + 42.dp.toPx(), center.y + 25.dp.toPx())
            close()
        }

        drawPath(
            path = path,
            color = AccentGold.copy(alpha = 0.35f)
        )
        drawPath(
            path = path,
            color = AccentGold,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Golden Sun above pyramids
        drawCircle(
            color = AccentGold,
            radius = 6.dp.toPx() * sunPulse,
            center = Offset(center.x, center.y - 28.dp.toPx())
        )

        // Center Arabic Question Mark "؟" inside the Main Pyramid in Royal Lapis Blue
        val textPaint = android.graphics.Paint().apply {
            color = AccentLapis.toArgb()
            textSize = 34.dp.toPx()
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                "؟",
                center.x,
                center.y + 18.dp.toPx(),
                textPaint
            )
        }
    }
}

// ==========================================
// SCREEN 2: MYSTERIOUS FORTUNE WHEEL SCREEN
// ==========================================
@Composable
fun WheelScreen(
    activePools: Map<Int, List<String>>,
    currentPlayerName: String,
    onCategorySelected: (Category) -> Unit,
    onStartGame: (Category) -> Unit,
    onBack: () -> Unit
) {
    val view = LocalView.current
    
    var isSpinning by remember { mutableStateOf(false) }
    var currentAngle by remember { mutableStateOf(0f) }
    var landedCategory by remember { mutableStateOf<Category?>(null) }
    var revealedCategory by remember { mutableStateOf<Category?>(null) }
    
    // Animate rotation angle
    val animRotationAngle by animateFloatAsState(
        targetValue = currentAngle,
        animationSpec = tween(
            durationMillis = 3800,
            easing = EaseOutCubic
        ),
        finishedListener = {
            isSpinning = false
            revealedCategory = landedCategory // REVEAL category name only after stopping!
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        },
        label = "WheelSpin"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color.White, CircleShape)
                        .border(1.dp, DividerWarm, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "العودة",
                        tint = AccentLapis
                    )
                }
                
                Text(
                    text = "عجلة الفئات الغامضة",
                    style = TextStyle(
                        color = AccentLapis,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                
                Spacer(modifier = Modifier.size(40.dp))
            }

            // Active Player Highlight Banner in Royal Pharaonic Gold and Lapis Blue
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentGold.copy(alpha = 0.15f))
                    .border(1.5.dp, AccentGold, RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "👑", fontSize = 16.sp)
                    Text(
                        text = "الملك الحالي: $currentPlayerName",
                        style = TextStyle(
                            color = TextDeepCoffee,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Center Mysterious Fortune Wheel (Category names are hidden until landed)
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                FortuneWheelCanvas(
                    rotationAngle = animRotationAngle,
                    categories = GameData.categories,
                    activePools = activePools
                )

                // Gold Crown pointer pointing down at the top center
                WheelPointer(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-14).dp)
                )
                
                // Center spin button hub - Egyptian sun emblem
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.dp, AccentGold, CircleShape)
                        .clickable(enabled = !isSpinning) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            SoundHelper.playSpinSound()
                            
                            val categoryList = GameData.categories
                            val chosenIndex = (0 until categoryList.size).random()
                            val selected = categoryList[chosenIndex]
                            
                            val segmentAngle = 360f / 7f
                            val sliceCenter = (chosenIndex * segmentAngle) + (segmentAngle / 2f)
                            val rotationOffset = 270f - sliceCenter
                            
                            val extraSpins = (6..8).random() * 360f
                            currentAngle += extraSpins + (rotationOffset - (currentAngle % 360f))
                            
                            isSpinning = true
                            revealedCategory = null // HIDE category from bottom until stopped
                            landedCategory = selected
                            onCategorySelected(selected)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لف",
                        style = TextStyle(
                            color = AccentLapis,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }

            // Selected category presentation & actions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSpinning) {
                    // Pulsing Mysterious text during spinning
                    val infiniteTransition = rememberInfiniteTransition(label = "SpinTextPulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "alpha"
                    )
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                            .border(1.dp, DividerWarm, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "🔮 طلاسم العجلة تدور الآن...",
                            style = TextStyle(
                                color = AccentLapis.copy(alpha = alpha),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "انتظر حتى يقع الاختيار وتنكشف الفئة!",
                            style = TextStyle(
                                color = TextLightBrown,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                } else {
                    AnimatedVisibility(
                        visible = revealedCategory != null,
                        enter = fadeIn() + expandVertically() + scaleIn(),
                        exit = fadeOut() + shrinkVertically() + scaleOut()
                    ) {
                        revealedCategory?.let { category ->
                            val isAvailable = (activePools[category.id]?.size ?: 0) > 0
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .background(Color.White, RoundedCornerShape(22.dp))
                                    .border(2.dp, AccentGold, RoundedCornerShape(22.dp))
                                    .padding(18.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(text = "✨", fontSize = 14.sp)
                                    Text(
                                        text = "انكشفت الفئة العشوائية!",
                                        style = TextStyle(
                                            color = AccentGold,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(text = "✨", fontSize = 14.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = category.name,
                                    style = TextStyle(
                                        color = AccentLapis,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Text(
                                    text = "الكلمات المتبقية في هذه الفئة: ${activePools[category.id]?.size ?: 0} كلمة",
                                    style = TextStyle(
                                        color = TextMediumBrown,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = { onStartGame(category) },
                                    enabled = isAvailable,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentLapis,
                                        disabledContainerColor = DividerWarm,
                                        contentColor = Color.White,
                                        disabledContentColor = TextLightBrown
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                ) {
                                    Text(
                                        text = if (isAvailable) "ابدأ سؤال $currentPlayerName 🚀" else "الفئة فارغة! لف مجدداً",
                                        style = TextStyle(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (revealedCategory == null) {
                        // Elegant instruction badge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                .border(1.dp, DividerWarm.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "اضغط على زر (لف) في منتصف العجلة\nوسيقوم المعبد باختيار فئة سرية وعشوائية لك!",
                                style = TextStyle(
                                    color = TextMediumBrown,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// MYSTERIOUS FORTUNE WHEEL CANVAS (NO LABELS)
// ==========================================
@Composable
fun FortuneWheelCanvas(
    rotationAngle: Float,
    categories: List<Category>,
    activePools: Map<Int, List<String>>
) {
    val densityVal = LocalDensity.current
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .rotate(rotationAngle)
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val segmentAngle = 360f / 7f

        // Draw 7 segments with respective category colors
        for (i in 0 until 7) {
            val category = categories[i]
            val color = Color(android.graphics.Color.parseColor(category.colorHex))
            val hasWords = (activePools[category.id]?.size ?: 0) > 0
            
            // Draw slice arc
            drawArc(
                color = if (hasWords) color else color.copy(alpha = 0.15f),
                startAngle = i * segmentAngle,
                sweepAngle = segmentAngle,
                useCenter = true,
                size = Size(radius * 2f, radius * 2f),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // Draw clean divider lines in Royal Gold
            val dividerAngleRad = Math.toRadians((i * segmentAngle).toDouble())
            val divX = (center.x + Math.cos(dividerAngleRad) * radius).toFloat()
            val divY = (center.y + Math.sin(dividerAngleRad) * radius).toFloat()
            drawLine(
                color = AccentGold.copy(alpha = 0.7f),
                start = center,
                end = Offset(divX, divY),
                strokeWidth = 2.dp.toPx()
            )
            
            // DRAW ENIGMATIC QUESTION MARKS INSIDE THE SLICES (to keep categories hidden!)
            val textPaint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.WHITE
                textSize = with(densityVal) { 18.sp.toPx() }
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                isSubpixelText = true
                isAntiAlias = true
            }

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val sliceCenterAngle = i * segmentAngle + segmentAngle / 2f
                
                nativeCanvas.save()
                nativeCanvas.translate(center.x, center.y)
                nativeCanvas.rotate(sliceCenterAngle)
                
                // Draw a gold/white mystery Arabic question mark inside the segment
                nativeCanvas.drawText(
                    "؟",
                    radius * 0.65f,
                    with(densityVal) { 6.dp.toPx() },
                    textPaint
                )
                nativeCanvas.restore()
            }
        }

        // Draw outer Pharaoh Gold frame ring
        drawCircle(
            color = AccentGold,
            radius = radius,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )

        // Draw golden pharaonic dots on the rim
        for (i in 0 until 14) {
            val rimAngleRad = Math.toRadians((i * (360f / 14f)).toDouble())
            val rimX = (center.x + Math.cos(rimAngleRad) * (radius - 5.dp.toPx())).toFloat()
            val rimY = (center.y + Math.sin(rimAngleRad) * (radius - 5.dp.toPx())).toFloat()
            drawCircle(
                color = AccentGold,
                radius = 3.5.dp.toPx(),
                center = Offset(rimX, rimY)
            )
        }
    }
}

@Composable
fun WheelPointer(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(28.dp, 36.dp)
    ) {
        val path = Path().apply {
            moveTo(size.width / 2f, size.height)
            lineTo(0f, 0f)
            lineTo(size.width, 0f)
            close()
        }
        
        drawPath(
            path = path,
            color = AccentGold
        )

        drawPath(
            path = path,
            color = AccentLapis,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

// ==========================================
// SCREEN 3: GAMEPLAY SCREEN (EXACTLY ONE WORD)
// ==========================================
@Composable
fun GameplayScreen(
    category: Category,
    currentWord: String,
    timeLeft: Int,
    currentPlayerName: String,
    onCorrect: () -> Unit,
    onSkip: () -> Unit,
    onEndGame: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit button
                TextButton(
                    onClick = onEndGame,
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, DividerWarm, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "انسحاب ❌",
                        style = TextStyle(
                            color = AccentClay,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Central Category & Player Pill
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "جولة الملك: $currentPlayerName",
                        style = TextStyle(
                            color = AccentGold,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.name,
                        style = TextStyle(
                            color = AccentLapis,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                }

                // Egyptian style Timer Widget
                TimerWidget(timeLeft = timeLeft)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Massive Responsive Card containing ONLY 1 Word to guess!
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
                    .border(2.dp, AccentGold, RoundedCornerShape(32.dp))
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "اشرح الكلمة التالية لـ $currentPlayerName ليربح!",
                        style = TextStyle(
                            color = TextLightBrown,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Text(
                        text = currentWord,
                        style = TextStyle(
                            color = AccentLapis,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCreamDark.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "💡 كلمة واحدة فقط تحسم النتيجة!",
                            style = TextStyle(
                                color = TextMediumBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Interactive "صح" (Success Nile Green) and "خطأ/تخطي" (Clay Red) Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentClay)
                        .clickable { onSkip() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "تخطي الكلمة",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "لم يعرف 😢",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentOlive)
                        .clickable { onCorrect() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "تخمين صحيح",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "صح! 🎉",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// ELEGANT TIMER COMPOSABLE
// ==========================================
@Composable
fun TimerWidget(timeLeft: Int) {
    val progress = timeLeft / 30f // Adjusted to 30 seconds
    val strokeColor = when {
        timeLeft > 15 -> AccentOlive
        timeLeft > 7 -> AccentGold
        else -> AccentClay
    }

    Box(
        modifier = Modifier.size(60.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = DividerWarm,
                style = Stroke(width = 3.dp.toPx())
            )
            drawArc(
                color = strokeColor,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$timeLeft",
                style = TextStyle(
                    color = TextDeepCoffee,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            )
            Text(
                text = "ثانية",
                style = TextStyle(
                    color = TextLightBrown,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

// ==========================================
// SCREEN 4: SCORE / SUMMARY SCREEN (EGYPTIAN LEADERBOARD)
// ==========================================
@Composable
fun ScoreScreen(
    category: Category,
    roundHistory: List<Pair<String, Boolean>>,
    currentPlayerName: String,
    playerScores: Map<String, Int>,
    playersList: List<String>,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit
) {
    val guessedSuccessfully = if (roundHistory.isNotEmpty()) roundHistory.first().second else false
    val wordPlayed = if (roundHistory.isNotEmpty()) roundHistory.first().first else "الكلمة السرية"

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Result Announcement Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(20.dp))
                        .border(1.5.dp, AccentGold, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "انتهت الجولة الفرعونية! 🏛️",
                        style = TextStyle(
                            color = AccentLapis,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }

            // Word Result Banner Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(1.dp, DividerWarm, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "محاولة الملك: $currentPlayerName",
                            style = TextStyle(
                                color = TextLightBrown,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (guessedSuccessfully) {
                            Text(
                                text = "🏆 تخمين صحيح! 👑",
                                style = TextStyle(
                                    color = AccentOlive,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "كسبت نقطة ذهبية جديدة!",
                                style = TextStyle(
                                    color = TextMediumBrown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        } else {
                            Text(
                                text = "😢 لم يتم التخمين",
                                style = TextStyle(
                                    color = AccentClay,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "حظاً أوفر للملك في المرة القادمة!",
                                style = TextStyle(
                                    color = TextMediumBrown,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Render the word in a polished style
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (guessedSuccessfully) AccentOlive.copy(alpha = 0.1f) else AccentClay.copy(alpha = 0.1f))
                                .border(1.dp, if (guessedSuccessfully) AccentOlive else AccentClay, RoundedCornerShape(12.dp))
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = wordPlayed,
                                style = TextStyle(
                                    color = TextDeepCoffee,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "الفئة: ${category.name}",
                            style = TextStyle(
                                color = TextLightBrown,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // MULTIPLAYER SCOREBOARD (جدول الصدارة الحالي)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(1.5.dp, AccentGold, RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = "🏆 لوحة الصدارة الملكية",
                        style = TextStyle(
                            color = AccentLapis,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    val sortedPlayers = playersList.sortedByDescending { playerScores[it] ?: 0 }
                    sortedPlayers.forEachIndexed { index, player ->
                        val score = playerScores[player] ?: 0
                        val medal = when(index) {
                            0 -> "🥇"
                            1 -> "🥈"
                            2 -> "🥉"
                            else -> "🏺"
                        }
                        
                        val isSelf = player == currentPlayerName
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelf) AccentGold.copy(alpha = 0.12f) else Color.Transparent)
                                .border(1.dp, if (isSelf) AccentGold else Color.Transparent, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$score نقطة",
                                style = TextStyle(
                                    color = AccentLapis,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = player,
                                    style = TextStyle(
                                        color = if (isSelf) AccentLapis else TextDeepCoffee,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = medal, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // Responsive Action Buttons Row
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(Color.White)
                            .border(1.dp, DividerWarm, RoundedCornerShape(27.dp))
                            .clickable { onHome() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "الرئيسية 🏛️",
                            style = TextStyle(
                                color = TextDeepCoffee,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(27.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(AccentLapis, AccentTurquoise)
                                )
                            )
                            .border(1.5.dp, AccentGold, RoundedCornerShape(27.dp))
                            .clickable { onPlayAgain() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "الملك التالي 🎮",
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
