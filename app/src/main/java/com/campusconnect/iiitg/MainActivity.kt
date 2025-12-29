package com.campusconnect.iiitg

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.google.firebase.firestore.Query
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.Firebase
import androidx.compose.material.icons.filled.Email
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import com.google.ai.client.generativeai.GenerativeModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import com.campusconnect.iiitg.utils.showBiometricPrompt
import com.google.firebase.auth.GoogleAuthProvider
import java.time.Instant.ofEpochMilli

data class Contact(
    val id: String = "", // Added this to track which document to delete
    val name: String = "",
    val role: String = "",
    val phone: String = "",
    val email: String = ""
)
// For sharing Study planner
fun shareStudyPlan(context: android.content.Context, planText: String) {
    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        // We use EXTRA_TEXT to supply the literal data to be sent
        putExtra(android.content.Intent.EXTRA_TEXT, planText)
        type = "text/plain" // The type of content is plain text
    }

    // createChooser ensures the app selector always appears
    val shareIntent = android.content.Intent.createChooser(sendIntent, "Share Study Plan via")
    context.startActivity(shareIntent)
}


// --- MAIN ACTIVITY ---

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    CampusSuperApp()
                }
            }
        }
    }
}

@Composable
fun CampusSuperApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = FirebaseFirestore.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var isUnlocked by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableStateOf("Dashboard") }
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            // We force lowercase on the document ID lookup
            db.collection("admins").document(currentUser!!.email!!.lowercase()).get()
                .addOnSuccessListener { isAdmin = it.exists() }
        } else {
            isAdmin = false
        }
    }

    BackHandler(enabled = currentScreen != "Dashboard") {
        currentScreen = "Dashboard"
    }

    if (!isUnlocked) {
        AuthGate(onSuccess = { isUnlocked = true })
    } else if (currentUser == null) {
        ManualLoginScreen(onLoginSuccess = { currentUser = auth.currentUser })
    } else {
        // --- UPDATED NAVIGATION LOGIC ---
        when (currentScreen) {
            "Dashboard" -> DashboardScreen(
                userEmail = currentUser?.email ?: "Student",
                onNavigate = { currentScreen = it },
                onLock = { isUnlocked = false },
                onLogout = {
                    auth.signOut()
                    currentUser = null
                    isUnlocked = false
                    isAdmin = false
                }
            )

            "Grievance" -> GrievanceModule(
                isAdminUser = isAdmin,
                onBack = { currentScreen = "Dashboard" }
            )

            "Academics" -> AcademicsModule(
                isAdminUser = isAdmin, // Pass the existing isAdmin state
                onBack = { currentScreen = "Dashboard" }
            )

            "Hostel" -> HostelModule(
                isAdminUser = isAdmin, // Essential for Warden view
                onBack = { currentScreen = "Dashboard" }
            )

            "Mess" -> MessModule(
                isAdmin = isAdmin, // Essential for Menu editing
                onBack = { currentScreen = "Dashboard" }
            )

            "Calendar" -> CalendarModule(
                isAdminUser = isAdmin, // Essential for Global Holiday management
                onBack = { currentScreen = "Dashboard" }
            )

            "Links" -> LinksModule(
                onBack = { currentScreen = "Dashboard" }
            )
        }
    }
}

// --- AUTHENTICATION & SECURITY ---

@Composable
fun AuthGate(onSuccess: () -> Unit) {
    val context = LocalContext.current as FragmentActivity
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- LOGO REPLACING LOCK ICON ---
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(140.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(24.dp))
        Text("App Locked", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Authenticate to access CampusConnect", textAlign = TextAlign.Center, color = Color.Gray)
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = { showBiometricPrompt(context, onSuccess = { onSuccess() }, onError = {}) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Unlock with Biometrics", fontSize = 18.sp)
        }
    }
}
@Composable
fun ManualLoginScreen(onLoginSuccess: () -> Unit) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val scrollState = rememberScrollState() // To handle screen overflow

    // --- FORGOT PASSWORD DIALOG ---
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("We will send a reset link to:")
                    Text(if(email.isEmpty()) "(Enter email first)" else email,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(
                    enabled = email.isNotEmpty() && email.contains("@"),
                    onClick = {
                        auth.sendPasswordResetEmail(email)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Reset link sent!", Toast.LENGTH_LONG).show()
                                showResetDialog = false
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                    }
                ) { Text("Send Link") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- LOGO BRANDING (Matches app_logo.jpeg) ---
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "CampusConnect Logo",
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 16.dp),
            contentScale = ContentScale.Fit
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (isSignUp) "Student Registration" else "Official Sign-In",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(20.dp))

                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim().lowercase() },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(24.dp))

                // Main Action Button
                Button(
                    onClick = {
                        if (email.isEmpty() || password.isEmpty()) {
                            Toast.makeText(context, "Fill all details", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true

                        if (isSignUp) {
                            if (email.endsWith("@iiitg.ac.in")) {
                                registerUser(email, password, auth, onLoginSuccess, context) { isLoading = false }
                            } else {
                                db.collection("admins").document(email).get()
                                    .addOnSuccessListener { doc ->
                                        if (doc.exists()) {
                                            registerUser(email, password, auth, onLoginSuccess, context) { isLoading = false }
                                        } else {
                                            isLoading = false
                                            Toast.makeText(context, "Whitelist check failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                        } else {
                            if (email.endsWith("@iiitg.ac.in")) {
                                attemptFirebaseLogin(email, password, auth, onLoginSuccess, context) { isLoading = false }
                            } else {
                                db.collection("admins").document(email).get()
                                    .addOnSuccessListener { doc ->
                                        if (doc.exists()) {
                                            attemptFirebaseLogin(email, password, auth, onLoginSuccess, context) { isLoading = false }
                                        } else {
                                            isLoading = false
                                            Toast.makeText(context, "Access Denied", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    else Text(if (isSignUp) "Register" else "Login")
                }

                Spacer(Modifier.height(16.dp))

                TextButton(
                    onClick = { isSignUp = !isSignUp },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSignUp) "Back to Login" else "New Student? Sign Up")
                }

                if (!isSignUp) {
                    TextButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Forgot Password?", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Helper function for Auth
private fun attemptFirebaseLogin(email: String, pass: String, auth: FirebaseAuth, onSuccess: () -> Unit, context: android.content.Context, onDone: () -> Unit) {
    auth.signInWithEmailAndPassword(email, pass)
        .addOnSuccessListener {
            onDone()
            onSuccess()
        }
        .addOnFailureListener { e ->
            onDone()
            Toast.makeText(context, "Login Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
}

private fun registerUser(email: String, pass: String, auth: FirebaseAuth, onSuccess: () -> Unit, context: android.content.Context, onDone: () -> Unit) {
    auth.createUserWithEmailAndPassword(email, pass)
        .addOnSuccessListener {
            onDone()
            onSuccess()
        }
        .addOnFailureListener { e ->
            onDone()
            Toast.makeText(context, "Registration Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
}

// Final Step: Sign in to Firebase with the Google Token
private fun completeLogin(auth: FirebaseAuth, idToken: String, onSuccess: () -> Unit) {
    val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(firebaseCredential).addOnCompleteListener { task ->
        if (task.isSuccessful) onSuccess()
    }
}

// --- DASHBOARD ---

@Composable
fun DashboardScreen(userEmail: String, onNavigate: (String) -> Unit, onLock: () -> Unit, onLogout: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Welcome,", style = MaterialTheme.typography.bodyLarge)
                Text(userEmail.substringBefore("@"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onLogout) { Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Logout", tint = Color.Red) }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Campus Services", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

        // Add Calendar to the modules list
        val modules = listOf(
            Triple("Grievance", "📢", MaterialTheme.colorScheme.primaryContainer),
            Triple("Academics", "📚", MaterialTheme.colorScheme.secondaryContainer),
            Triple("Hostel", "🏠", MaterialTheme.colorScheme.tertiaryContainer),
            Triple("Mess", "🍱", Color(0xFFFFE0B2)),
            Triple("Calendar", "📅", Color(0xFFC8E6C9)), // New Tab
            Triple("Links", "🔗", Color(0xFFE1BEE7))
        )

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            modules.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (title, emoji, color) ->
                        Card(
                            onClick = { onNavigate(title) },
                            modifier = Modifier.weight(1f).height(140.dp).padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = color)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(emoji, fontSize = 40.sp)
                                    Text(title, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(onClick = onLock, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock session")
                Spacer(Modifier.width(8.dp))
                Text("Secure App Session")
            }
        }
    }
}

// --- MESS MODULE ---

// --- MESS MODULE ---

// --- MESS MODULE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessModule(isAdmin: Boolean, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var selectedDay by remember { mutableStateOf("Monday") }

    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"

    // Menu States
    var breakfast by remember { mutableStateOf("") }
    var lunch by remember { mutableStateOf("") }
    var dinner by remember { mutableStateOf("") }

    // --- REBATE & HISTORY STATES ---
    var rebateFrom by remember { mutableStateOf("") }
    var rebateTo by remember { mutableStateOf("") }
    var rebateReason by remember { mutableStateOf("") }
    var rebateHistory by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    // Date Picker States
    var showDatePicker by remember { mutableStateOf(false) }
    var activeDateField by remember { mutableStateOf("from") }
    val datePickerState = rememberDatePickerState()

    // Fetch menu
    LaunchedEffect(selectedDay) {
        db.collection("mess_menu").document(selectedDay).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    breakfast = doc.getString("breakfast") ?: ""
                    lunch = doc.getString("lunch") ?: ""
                    dinner = doc.getString("dinner") ?: ""
                }
            }
    }

    // --- FETCH REBATE HISTORY (Type-Explicit Fix) ---
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            val query = if (isAdmin) {
                db.collection("mess_rebates")
            } else {
                db.collection("mess_rebates").whereEqualTo("userEmail", userEmail)
            }

            query.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) {
                        // FIX: Explicitly define the map type to fix the inference error
                        rebateHistory = snap.documents.map { doc ->
                            val data = doc.data ?: emptyMap<String, Any>()
                            data + ("docId" to doc.id)
                        }
                    }
                }
        }
    }

    // --- DATE PICKER DIALOG ---
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate().toString()
                        if (activeDateField == "from") rebateFrom = date else rebateTo = date
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("IIITG Mess Portal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(vertical = 16.dp)) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Menu") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Apply") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("History") })
        }

        when (selectedTab) {
            0 -> {
                ScrollableTabRow(selectedTabIndex = days.indexOf(selectedDay), edgePadding = 0.dp) {
                    days.forEach { d -> Tab(selected = selectedDay == d, onClick = { selectedDay = d }, text = { Text(d) }) }
                }
                Spacer(Modifier.height(24.dp))
                Text(text = if (isAdmin) "Admin Mode: Editing $selectedDay" else "$selectedDay's Menu", style = MaterialTheme.typography.titleLarge)
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = breakfast, onValueChange = { if (isAdmin) breakfast = it }, label = { Text("Breakfast") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), readOnly = !isAdmin)
                    OutlinedTextField(value = lunch, onValueChange = { if (isAdmin) lunch = it }, label = { Text("Lunch") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), readOnly = !isAdmin)
                    OutlinedTextField(value = dinner, onValueChange = { if (isAdmin) dinner = it }, label = { Text("Dinner") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), readOnly = !isAdmin)
                    if (isAdmin) {
                        Button(onClick = {
                            // FIX: Explicitly define HashMap types
                            val updatedMenu = hashMapOf<String, Any>("breakfast" to breakfast, "lunch" to lunch, "dinner" to dinner)
                            db.collection("mess_menu").document(selectedDay).set(updatedMenu).addOnSuccessListener { Toast.makeText(context, "Menu updated!", Toast.LENGTH_SHORT).show() }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Save Changes") }
                    }
                }
            }
            1 -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Apply for Mess Rebate", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = rebateFrom, onValueChange = { }, label = { Text("From Date") }, readOnly = true, modifier = Modifier.fillMaxWidth().clickable { activeDateField = "from"; showDatePicker = true }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = Color.Black, disabledBorderColor = Color.Gray), trailingIcon = { Icon(Icons.Default.DateRange, null) })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = rebateTo, onValueChange = { }, label = { Text("To Date") }, readOnly = true, modifier = Modifier.fillMaxWidth().clickable { activeDateField = "to"; showDatePicker = true }, enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = Color.Black, disabledBorderColor = Color.Gray), trailingIcon = { Icon(Icons.Default.DateRange, null) })
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = rebateReason, onValueChange = { rebateReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())

                    Button(onClick = {
                        if (rebateFrom.isEmpty() || rebateTo.isEmpty()) {
                            Toast.makeText(context, "Select dates", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // FIX: Logic check for date range
                        if (rebateFrom > rebateTo) {
                            Toast.makeText(context, "Error: 'From' date cannot be after 'To' date", Toast.LENGTH_LONG).show()
                            return@Button
                        }

                        // FIX: Explicit HashMap type <String, Any>
                        val reb = hashMapOf<String, Any>(
                            "from" to rebateFrom,
                            "to" to rebateTo,
                            "reason" to rebateReason,
                            "userEmail" to userEmail,
                            "status" to "Pending",
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("mess_rebates").add(reb).addOnSuccessListener {
                            Toast.makeText(context, "Submitted!", Toast.LENGTH_SHORT).show()
                            rebateFrom = ""; rebateTo = ""; rebateReason = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(56.dp)) { Text("Submit Rebate Request") }
                }
            }
            2 -> {
                if (rebateHistory.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No history found") }
                } else {
                    LazyColumn {
                        items(rebateHistory) { rebate ->
                            RebateHistoryCard(rebate, isAdmin, db)
                        }
                    }
                }
            }
        }
    }
}

// --- NEW: REBATE HISTORY CARD ---
@Composable
fun RebateHistoryCard(rebate: Map<String, Any>, isAdmin: Boolean, db: FirebaseFirestore) {
    val context = LocalContext.current // Added to show Toasts
    val status = rebate["status"]?.toString() ?: "Pending"
    val docId = rebate["docId"]?.toString() ?: ""
    val color = when(status) {
        "Approved" -> Color(0xFF4CAF50)
        "Rejected" -> Color.Red
        else -> Color(0xFFFFA500)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${rebate["from"]} to ${rebate["to"]}", fontWeight = FontWeight.Bold)
                Text(status.uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Text("Reason: ${rebate["reason"]}", fontSize = 12.sp)
            if (isAdmin) Text("By: ${rebate["userEmail"]}", fontSize = 10.sp, color = Color.Gray)

            if (isAdmin && status == "Pending") {
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { db.collection("mess_rebates").document(docId).update("status", "Approved") }, modifier = Modifier.weight(1f)) { Text("Approve") }
                    OutlinedButton(onClick = { db.collection("mess_rebates").document(docId).update("status", "Rejected") }, modifier = Modifier.weight(1f)) { Text("Reject") }
                }
            } else if (!isAdmin && status == "Pending") {
                // FIXED: Better error handling for the delete operation
                TextButton(onClick = {
                    if (docId.isNotEmpty()) {
                        db.collection("mess_rebates").document(docId).delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Rebate Cancelled", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }) {
                    Text("Cancel Request", color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun MessItem(title: String, time: String, menu: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.Bold); Text(time, style = MaterialTheme.typography.bodySmall)
        }
        Text(menu, style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

// --- HOSTEL MODULE ---

// --- UPDATED HOSTEL MODULE (LEAVE HISTORY & WITHDRAW) ---
// --- FIXED HOSTEL MODULE ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostelModule(isAdminUser: Boolean, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"

    // --- LEAVE FORM STATES ---
    var destination by remember { mutableStateOf("") }
    var departureDate by remember { mutableStateOf("") }
    var parentContact by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // --- DATA LIST FOR HISTORY ---
    var leaveData by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    // --- FETCH LEAVE HISTORY ---
    DisposableEffect(selectedTab, isAdminUser) {
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        if (selectedTab == 2) {
            val collectionRef = db.collection("leaves")
            val query = if (isAdminUser) collectionRef else collectionRef.whereEqualTo("userEmail", userEmail)

            listener = query.orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snap, error ->
                    if (snap != null) {
                        leaveData = snap.documents.map { doc ->
                            val m = doc.data?.toMutableMap() ?: mutableMapOf()
                            m["docId"] = doc.id
                            m.toMap()
                        }
                    }
                }
        }
        onDispose { listener?.remove() }
    }

    // --- DATE PICKER DIALOG ---
    if (showDatePicker) {
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let {
                    departureDate = ofEpochMilli(it)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().toString()
                }
                showDatePicker = false
            }) { Text("OK") }
        }) { DatePicker(state = datePickerState) }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        // --- HEADER ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Hostel Hub", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        // --- TABS ---
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(vertical = 16.dp)) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Leave") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Dir.") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text(if (isAdminUser) "All Requests" else "History") })
        }

        when (selectedTab) {
            // --- TAB 0: LEAVE APPLICATION ---
            0 -> {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Apply for Outstation Leave", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(value = destination, onValueChange = { destination = it }, label = { Text("Destination") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = departureDate, onValueChange = { }, readOnly = true, label = { Text("Departure Date") }, modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, "Select Date") } }
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = parentContact, onValueChange = { parentContact = it }, label = { Text("Parent's Contact") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (destination.isNotBlank() && departureDate.isNotBlank()) {
                                val leave = hashMapOf(
                                    "destination" to destination, "departureDate" to departureDate, "parentContact" to parentContact,
                                    "userEmail" to userEmail, "status" to "Pending", "timestamp" to com.google.firebase.Timestamp.now()
                                )
                                db.collection("leaves").add(leave).addOnSuccessListener {
                                    Toast.makeText(context, "Submitted Successfully!", Toast.LENGTH_SHORT).show()
                                    destination = ""; departureDate = ""; parentContact = ""
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)
                    ) { Text("Submit for Approval") }
                }
            }

            // --- TAB 1: HOSTEL DIRECTORY (NEW DYNAMIC VERSION) ---
            1 -> {
                HostelDirectory(isAdmin = isAdminUser)
            }

            // --- TAB 2: HISTORY / ADMIN APPROVAL ---
            2 -> {
                if (leaveData.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No records found") }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(leaveData) { leave ->
                            AdminLeaveCard(leave, isAdminUser, db)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminLeaveCard(leave: Map<String, Any>, isAdmin: Boolean, db: FirebaseFirestore) {
    val status = leave["status"]?.toString() ?: "Pending"
    val docId = leave["docId"]?.toString() ?: ""
    val statusColor = when (status) {
        "Approved" -> Color(0xFF4CAF50)
        "Rejected" -> Color(0xFFF44336)
        else -> Color(0xFFFFA500)
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(leave["destination"]?.toString() ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Surface(color = statusColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(status.uppercase(), color = statusColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("Applied by: ${leave["userEmail"]}", fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("Departure: ${leave["departureDate"]}")
            Text("Parent: ${leave["parentContact"]}")

            if (isAdmin && status == "Pending") {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { if(docId.isNotEmpty()) db.collection("leaves").document(docId).update("status", "Approved") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Approve") }
                    OutlinedButton(onClick = { if(docId.isNotEmpty()) db.collection("leaves").document(docId).update("status", "Rejected") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)) { Text("Reject") }
                }
            } else if (!isAdmin && status == "Pending") {
                TextButton(onClick = { if(docId.isNotEmpty()) db.collection("leaves").document(docId).delete() }) { Text("Withdraw Request", color = Color.Red) }
            }
        }
    }
}

@Composable
fun ContactCard(role: String, name: String, phone: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ListItem(
            headlineContent = { Text(role, fontWeight = FontWeight.Bold) },
            supportingContent = { Text("$name | $phone") },
            leadingContent = { Icon(Icons.Default.Phone, contentDescription = null) }
        )
    }
}

// --- ACADEMICS MODULE ---

// --- ACADEMICS MODULE ---

@Composable
fun AcademicsModule(isAdminUser: Boolean, onBack: () -> Unit) { // Added parameter
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Study AI", "GPA Calc", "Courses", "Attendance", "Directory")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Academic Hub",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 16.dp,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> AIStudyPlanner()
                1 -> GradeCalculator()
                2 -> CourseManager()
                3 -> AttendanceTab()
                4 -> PhoneDirectory(isAdmin = isAdminUser) // Passing the boolean here
            }
        }
    }
}

// --- 1. AI STUDY PLANNER (Powered by Gemini) ---
@Composable
fun AIStudyPlanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Initialize Gemini 2.0 Flash
    val generativeModel = remember {
        GenerativeModel(
            modelName = "gemini-2.5-flash",
            apiKey = "AIzaSyCMHP9WLk2R4vLo14AwUszobGvsbLugzJ8" // Paste your key here
        )
    }

    var userInput by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Gemini Study Assistant",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Input Field
        OutlinedTextField(
            value = userInput,
            onValueChange = { userInput = it },
            label = { Text("Enter topics or exam date") },
            placeholder = { Text("e.g., Data Structures Midterm on Monday") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Generate Button
        Button(
            onClick = {
                if (userInput.isNotBlank()) {
                    isLoading = true
                    scope.launch {
                        try {
                            val result = generativeModel.generateContent("Create a study plan for: $userInput")
                            aiResponse = result.text ?: "No response received."
                        } catch (e: Exception) {
                            aiResponse = "Error: ${e.localizedMessage}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp), // Fixed height helps visibility
            enabled = !isLoading,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary // Forces text/icon to be visible
            )
        ) {
            // We use a Box to keep things centered and stable
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "Generate Study Plan",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Result Area
        if (aiResponse.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Your Schedule", fontWeight = FontWeight.Bold)

                        // --- THE SHARE BUTTON ---
                        IconButton(onClick = { shareStudyPlan(context, aiResponse) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(text = aiResponse, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// --- 2. SPI / CPI CALCULATOR ---
// --- 2. SPI / CPI CALCULATOR (Updated with variable credits) ---
@Composable
fun GradeCalculator() {
    var currentCpi by remember { mutableStateOf("") }
    var creditsDone by remember { mutableStateOf("") }
    var targetCpi by remember { mutableStateOf("") }
    var calculationResult by remember { mutableStateOf("") }

    // 1. ADD THE STATE VARIABLE HERE
    var totalProgramCredits by remember { mutableStateOf("160") } // Default to 160

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("SPI/CPI Goal Setting", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        // 2. ADD THE TEXTFIELD TO YOUR UI HERE
        OutlinedTextField(
            value = totalProgramCredits,
            onValueChange = { totalProgramCredits = it },
            label = { Text("Total Program Credits") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = currentCpi, onValueChange = { currentCpi = it }, label = { Text("Current CPI") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = creditsDone, onValueChange = { creditsDone = it }, label = { Text("Total Credits Completed") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = targetCpi, onValueChange = { targetCpi = it }, label = { Text("Desired Final CPI") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

        Button(
            onClick = {
                val cur = currentCpi.toDoubleOrNull() ?: 0.0
                val done = creditsDone.toDoubleOrNull() ?: 0.0
                val target = targetCpi.toDoubleOrNull() ?: 0.0

                // 3. UPDATE THE CALCULATION LOGIC HERE
                val totalCredits = totalProgramCredits.toDoubleOrNull() ?: 160.0
                val remaining = totalCredits - done

                if (remaining <= 0) {
                    calculationResult = "You have already completed the credits!"
                } else {
                    val neededSPI = ((target * totalCredits) - (cur * done)) / remaining
                    calculationResult = if (neededSPI > 10.0) "Impossible Target (Need > 10 SPI)"
                    else "You need an average of ${String.format("%.2f", neededSPI)} SPI in remaining semesters."
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) { Text("Check Feasibility") }

        if (calculationResult.isNotEmpty()) {
            Card(modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Text(calculationResult, modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

// --- 3. COURSE MANAGEMENT ---
@Composable
fun CourseManager() {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"

    // State variables
    var courseList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newCourseName by remember { mutableStateOf("") }
    var newCourseCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // 1. Fetch courses from Firestore in real-time
    LaunchedEffect(Unit) {
        db.collection("user_courses")
            .whereEqualTo("userEmail", userEmail)
            .addSnapshotListener { snap, error ->
                if (snap != null) {
                    // We save the document ID as "docId" so we can delete it later
                    courseList = snap.documents.map { doc ->
                        doc.data?.plus("docId" to doc.id) ?: emptyMap()
                    }
                }
                isLoading = false
            }
    }

    // 2. The Pop-up Dialog to Add a Course
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add New Course") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCourseCode,
                        onValueChange = { newCourseCode = it.uppercase() },
                        label = { Text("Course Code (e.g. CS301)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCourseName,
                        onValueChange = { newCourseName = it },
                        label = { Text("Course Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newCourseName.isNotBlank() && newCourseCode.isNotBlank()) {
                        val courseData = hashMapOf(
                            "courseName" to newCourseName,
                            "courseCode" to newCourseCode,
                            "userEmail" to userEmail,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )

                        // Save to Firebase
                        db.collection("user_courses").add(courseData)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Saved to Database!", Toast.LENGTH_SHORT).show()
                                showAddDialog = false
                                newCourseName = ""
                                newCourseCode = ""
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 3. The Main Screen UI
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Courses", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(30.dp))
            }
        } else if (courseList.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(60.dp))
                Text("No courses added yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(courseList) { course ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = course["courseCode"].toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = course["courseName"].toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // DELETE BUTTON
                            IconButton(onClick = {
                                val id = course["docId"].toString()
                                db.collection("user_courses").document(id).delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Course Deleted", Toast.LENGTH_SHORT).show()
                                    }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceTab() {
    val db = FirebaseFirestore.getInstance()
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"

    var courseList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var attendanceData by remember { mutableStateOf<Map<String, Map<String, Any>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        db.collection("user_courses").whereEqualTo("userEmail", userEmail)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    courseList = snap.documents.map { it.data?.plus("docId" to it.id) ?: emptyMap() }
                }
            }

        db.collection("attendance").whereEqualTo("userEmail", userEmail)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val map = mutableMapOf<String, Map<String, Any>>()
                    snap.documents.forEach { doc ->
                        val data = doc.data ?: emptyMap()
                        val courseId = data["courseId"].toString()
                        map[courseId] = data.plus("attDocId" to doc.id)
                    }
                    attendanceData = map
                }
                isLoading = false
            }
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(courseList) { course ->
                val courseId = course["docId"].toString()
                val data = attendanceData[courseId] ?: emptyMap()

                val attended = (data["attended"] as? Long)?.toInt() ?: 0
                val total = (data["total"] as? Long)?.toInt() ?: 0
                val percentage = if (total > 0) (attended.toFloat() / total.toFloat()) else 0f

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(course["courseName"].toString(), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(course["courseCode"].toString(), color = Color.Gray, fontSize = 12.sp)

                        Spacer(Modifier.height(12.dp))

                        // Progress Bar - Simplified to avoid 'clip' error
                        LinearProgressIndicator(
                            progress = { percentage },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            color = if (percentage < 0.75f) Color.Red else Color(0xFF4CAF50),
                            trackColor = Color.LightGray.copy(0.3f)
                        )

                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(percentage * 100).toInt()}% Attendance", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(if (percentage < 0.75f) "Below 75%!" else "Safe", fontSize = 12.sp, color = if (percentage < 0.75f) Color.Red else Color(0xFF4CAF50))
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            // Attended
                            AttendanceCounter(
                                label = "Attended",
                                count = attended,
                                onUpdate = { newAttended ->
                                    if (newAttended <= total) {
                                        updateAttendance(db, userEmail, courseId, data["attDocId"]?.toString(), newAttended, total)
                                    }
                                }
                            )

                            // Held
                            AttendanceCounter(
                                label = "Held",
                                count = total,
                                onUpdate = { newTotal ->
                                    val adjustedAttended = if (attended > newTotal) newTotal else attended
                                    updateAttendance(db, userEmail, courseId, data["attDocId"]?.toString(), adjustedAttended, newTotal)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceCounter(label: String, count: Int, onUpdate: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (count > 0) onUpdate(count - 1) }) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(32.dp))
            }
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = { onUpdate(count + 1) }) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(32.dp))
            }
        }
    }
}
fun updateAttendance(db: FirebaseFirestore, email: String, courseId: String, docId: String?, attended: Int, total: Int) {
    val data = hashMapOf(
        "userEmail" to email,
        "courseId" to courseId,
        "attended" to attended,
        "total" to total,
        "lastUpdated" to com.google.firebase.Timestamp.now()
    )

    if (docId != null) {
        // Update existing record
        db.collection("attendance").document(docId).set(data)
    } else {
        // Create first-time record
        db.collection("attendance").add(data)
    }
}


@Composable
fun AcademicClassItem(name: String, time: String, loc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Text(name, fontWeight = FontWeight.Bold); Text(loc, style = MaterialTheme.typography.bodySmall) }
        Text(time, color = MaterialTheme.colorScheme.primary)
    }
}

// --- GRIEVANCE MODULE ---


@Composable
fun GrievanceModule(isAdminUser: Boolean, onBack: () -> Unit) {
    var activeSubTab by remember { mutableStateOf("Boy's Hostel") }
    val db = FirebaseFirestore.getInstance()
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"

    BackHandler(enabled = activeSubTab != "Boy's Hostel") { activeSubTab = "Boy's Hostel" }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Grievance Portal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        val tabs = listOf("Boy's Hostel", "Girl's Hostel", "SAC", "Academic Block", "Others", "Status")
        ScrollableTabRow(selectedTabIndex = tabs.indexOf(activeSubTab), edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
            tabs.forEach { tab ->
                Tab(selected = activeSubTab == tab, onClick = { activeSubTab = tab }, text = { Text(tab, fontSize = 12.sp) })
            }
        }
        Spacer(Modifier.height(24.dp))

        if (activeSubTab == "Status") {
            GrievanceStatusList(db, userEmail, isAdminUser)
        } else {
            GrievanceForm(activeSubTab, db, userEmail)
        }
    }
}

@Composable
fun GrievanceStatusList(db: FirebaseFirestore, currentUserEmail: String, isAdmin: Boolean) {
    var grievanceList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

    LaunchedEffect(Unit) {
        db.collection("all_grievances")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    grievanceList = snapshot.documents.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["docId"] = doc.id
                        data
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = if (isAdmin) "Admin Control: All Complaints" else "Your Submissions", fontWeight = FontWeight.Bold, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        if (grievanceList.isEmpty()) {
            Text("No complaints found.", modifier = Modifier.padding(16.dp))
        }

        LazyColumn {
            items(grievanceList) { data ->
                if (isAdmin || data["userEmail"] == currentUserEmail) {
                    GrievanceTicketCard(data, isAdmin, db)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrievanceForm(category: String, db: FirebaseFirestore, email: String) {
    val context = LocalContext.current
    var isSubmitting by remember { mutableStateOf(false) }

    var expandedProblem by remember { mutableStateOf(false) }
    var selectedProblem by remember { mutableStateOf("Select Problem Type") }
    var locationInput by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expandedAnnex by remember { mutableStateOf(false) }
    var selectedAnnex by remember { mutableStateOf("Select Annex") }
    val annexOptions = listOf("Annex-1", "Annex-2A", "Annex-2B", "Old Boy's Hostel")

    val isBoysHostel = category == "Boy's Hostel"
    val isHostel = category.contains("Hostel")
    val isOthers = category == "Others"
    val isSAC = category == "SAC"

    val problems = when (category) {
        "Boy's Hostel", "Girl's Hostel" -> listOf("Electrical", "Plumbing", "Carpentry", "Wifi/Internet", "Cleaning", "Others")
        "Academic Block", "SAC" -> listOf("Furniture", "AC/Fan", "Water Dispenser", "Cleanliness", "Equipments", "Others")
        else -> listOf("General Complaint", "Suggestion", "Security Issue", "Others")
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Report Issue for: $category", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))

        if (isBoysHostel) {
            ExposedDropdownMenuBox(expanded = expandedAnnex, onExpandedChange = { expandedAnnex = !expandedAnnex }) {
                OutlinedTextField(
                    value = selectedAnnex, onValueChange = {}, readOnly = true, label = { Text("Select Hostel Annex") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAnnex) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expandedAnnex, onDismissRequest = { expandedAnnex = false }) {
                    annexOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = { selectedAnnex = option; expandedAnnex = false })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        ExposedDropdownMenuBox(expanded = expandedProblem, onExpandedChange = { expandedProblem = !expandedProblem }) {
            OutlinedTextField(
                value = selectedProblem, onValueChange = {}, readOnly = true, label = { Text("Problem Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProblem) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expandedProblem, onDismissRequest = { expandedProblem = false }) {
                problems.forEach { problem ->
                    DropdownMenuItem(text = { Text(problem) }, onClick = { selectedProblem = problem; expandedProblem = false })
                }
            }
        }

        if (!isOthers) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = locationInput,
                onValueChange = { newValue ->
                    if (isHostel) { if (newValue.all { it.isDigit() }) locationInput = newValue }
                    else { locationInput = newValue }
                },
                label = { Text(if (isHostel) "Room Number" else if(isSAC) "Hall Name" else "Floor") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().height(150.dp))

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                val isAnnexSelected = !isBoysHostel || (isBoysHostel && selectedAnnex != "Select Annex")
                if (selectedProblem != "Select Problem Type" && description.isNotBlank() && isAnnexSelected) {
                    isSubmitting = true

                    // Direct Text-Only Submission
                    val report = hashMapOf(
                        "category" to category,
                        "annex" to if (isBoysHostel) selectedAnnex else "N/A",
                        "problemType" to selectedProblem,
                        "location" to if (isOthers) "N/A" else locationInput,
                        "description" to description,
                        "imageUrl" to "none", // Placeholder string
                        "userEmail" to email,
                        "status" to "Pending",
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )

                    db.collection("all_grievances").add(report)
                        .addOnSuccessListener {
                            isSubmitting = false
                            selectedProblem = "Select Problem Type"; selectedAnnex = "Select Annex"
                            locationInput = ""; description = ""
                            Toast.makeText(context, "Complaint Logged Successfully", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            isSubmitting = false
                            Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSubmitting
        ) {
            if (isSubmitting) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            else Text("Submit Grievance")
        }
    }
}

@Composable
fun GrievanceTicketCard(data: Map<String, Any>, isAdmin: Boolean, db: FirebaseFirestore) {
    val docId = data["docId"] as? String ?: ""
    val currentStatus = data["status"] as? String ?: "Pending"
    var showStatusMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${data["category"]} - ${data["problemType"]}", fontWeight = FontWeight.Bold)
                    Text("Loc: ${data["location"]} (${data["annex"]})", style = MaterialTheme.typography.bodySmall)
                }
                val badgeColor = when (currentStatus) { "Resolved" -> Color(0xFF4CAF50); "In Progress" -> Color(0xFF2196F3); else -> Color(0xFFFFA500) }
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable(enabled = isAdmin) { showStatusMenu = true }
                ) {
                    Text(text = currentStatus.uppercase(), color = badgeColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            }

            Text(text = data["description"].toString(), modifier = Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)

            if (isAdmin) {
                Text("Filed by: ${data["userEmail"]}", fontSize = 10.sp, color = Color.Gray)
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    listOf("Pending", "In Progress", "Resolved").forEach { status ->
                        DropdownMenuItem(text = { Text(status) }, onClick = {
                            if (docId.isNotEmpty()) { db.collection("all_grievances").document(docId).update("status", status) }
                            showStatusMenu = false
                        })
                    }
                }
            }
        }
    }
}


// --- LINKS MODULE ---

@Composable
fun LinksModule(onBack: () -> Unit) {
    val handler = LocalUriHandler.current
    val scroll = rememberScrollState()

    Column(modifier = Modifier.padding(16.dp).fillMaxSize().verticalScroll(scroll)) {
        TextButton(onClick = onBack) { Text("← Dashboard") }
        Text("Institute Directory", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        LinkSection("CAMPUS INTRANET (Local Only)", listOf("Intranet Portal" to "http://172.16.1.11"), handler)
        Spacer(Modifier.height(24.dp))
        LinkSection("INSTITUTE ACADEMICS", listOf(
            "Institute Website" to "https://www.iiitg.ac.in",
            "Academic Calendar" to "https://www.iiitg.ac.in/calendar",
            "Syllabus/Curriculum" to "https://www.iiitg.ac.in/curriculum",
            "Faculty Profiles" to "https://www.iiitg.ac.in/faculty",
            "SBI Collect (Fee)" to "https://www.onlinesbi.sbi/sbicollect/"
        ), handler)
        Spacer(Modifier.height(24.dp))
        LinkSection("GOVERNMENT PORTALS", listOf(
            "Scholarship Portal" to "https://scholarships.gov.in",
            "SWAYAM NPTEL" to "https://swayam.gov.in",
            "DigiLocker" to "https://www.digilocker.gov.in",
            "UGC India" to "https://www.ugc.ac.in",
            "National Academic Depository" to "https://nad.gov.in"
        ), handler)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun LinkSection(title: String, links: List<Pair<String, String>>, handler: androidx.compose.ui.platform.UriHandler) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        links.forEach { (name, url) ->
            OutlinedButton(onClick = { handler.openUri(url) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name); Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
// --- NATIONAL HOLIDAY REGISTRY ---
fun getNationalHoliday(month: Int, day: Int): String? {
    return when ("$month-$day") {
        "1-26" -> "Republic Day"
        "8-15" -> "Independence Day"
        "10-2" -> "Gandhi Jayanti"
        "12-25" -> "Christmas"
        "1-1" -> "New Year's Day"
        "5-1" -> "Labour Day"
        "10-31" -> "Sardar Patel Jayanti"
        else -> null
    }
}

// --- CALENDAR MODULE ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarModule(isAdminUser: Boolean, onBack: () -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: "Unknown"

    // --- DATE CALCULATIONS ---
    var monthOffset by remember { mutableIntStateOf(0) }
    val today = java.util.Calendar.getInstance()

    val displayCalendar = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.MONTH, monthOffset)
    }
    val displayMonth = displayCalendar.get(java.util.Calendar.MONTH) + 1
    val displayYear = displayCalendar.get(java.util.Calendar.YEAR)

    val monthNames = listOf("January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December")
    val monthName = monthNames[displayMonth - 1]

    // --- STATE ---
    var globalEvents by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var personalEvents by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedDateForEvent by remember { mutableStateOf("") }
    var newEventTitle by remember { mutableStateOf("") }
    var isHolidayEntry by remember { mutableStateOf(false) }

    // --- LISTENERS ---
    LaunchedEffect(monthOffset) {
        db.collection("campus_calendar").addSnapshotListener { snap, _ ->
            if (snap != null) globalEvents = snap.documents.map { it.data?.plus("docId" to it.id) ?: emptyMap() }
        }
        db.collection("user_events").whereEqualTo("userEmail", userEmail).addSnapshotListener { snap, _ ->
            if (snap != null) personalEvents = snap.documents.map { it.data?.plus("docId" to it.id) ?: emptyMap() }
        }
    }

    // --- ADD EVENT DIALOG ---
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Event for $selectedDateForEvent") },
            text = {
                Column {
                    OutlinedTextField(value = newEventTitle, onValueChange = { newEventTitle = it }, label = { Text("Event Title") })
                    if (isAdminUser) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            Checkbox(checked = isHolidayEntry, onCheckedChange = { isHolidayEntry = it })
                            Text("Mark as Global Holiday")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val collection = if (isHolidayEntry && isAdminUser) "campus_calendar" else "user_events"
                    db.collection(collection).add(hashMapOf(
                        "title" to newEventTitle,
                        "date" to selectedDateForEvent,
                        "type" to if (isHolidayEntry) "Holiday" else "Personal",
                        "userEmail" to userEmail
                    ))
                    showAddDialog = false
                    newEventTitle = ""
                    isHolidayEntry = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        // --- HEADER ---
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Campus Calendar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

            // GO TO TODAY BUTTON (Only shows if you scrolled away)
            if (monthOffset != 0) {
                TextButton(onClick = { monthOffset = 0 }) {
                    Text("Today", fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- MONTH SELECTOR ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { monthOffset-- }) { Icon(Icons.Default.KeyboardArrowLeft, "Prev") }
            Text("$monthName $displayYear", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { monthOffset++ }) { Icon(Icons.Default.KeyboardArrowRight, "Next") }
        }

        Text("Long press dates to add events", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))

        // --- CALENDAR GRID ---
        CalendarGrid(
            displayMonth = displayMonth,
            displayYear = displayYear,
            globalEvents = globalEvents,
            personalEvents = personalEvents,
            monthOffset = monthOffset,
            onDateLongClick = { date ->
                selectedDateForEvent = date
                showAddDialog = true
            }
        )

        // --- LEGEND ---
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendItem(Color.Black, "Today")
            LegendItem(Color(0xFFF44336), "Holiday")
            LegendItem(Color(0xFF2196F3), "Event")
        }

        Spacer(Modifier.height(16.dp))
        Text("Events in $monthName", fontWeight = FontWeight.Bold)

        LazyColumn(modifier = Modifier.weight(1f)) {
            // National Holidays logic
            item {
                for (day in 1..31) {
                    val holiday = getNationalHoliday(displayMonth, day)
                    if (holiday != null) {
                        EventItemRow(mapOf("title" to holiday, "date" to "$displayYear-$displayMonth-$day"), false, db, "")
                    }
                }
            }
            // DB Events
            items(globalEvents.filter { it["date"].toString().startsWith("$displayYear-$displayMonth-") }) { event ->
                EventItemRow(event, isAdminUser, db, "campus_calendar")
            }
            items(personalEvents.filter { it["date"].toString().startsWith("$displayYear-$displayMonth-") }) { event ->
                EventItemRow(event, true, db, "user_events")
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarGrid(
    displayMonth: Int,
    displayYear: Int,
    globalEvents: List<Map<String, Any>>,
    personalEvents: List<Map<String, Any>>,
    monthOffset: Int,
    onDateLongClick: (String) -> Unit
) {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.YEAR, displayYear)
        set(java.util.Calendar.MONTH, displayMonth - 1)
        set(java.util.Calendar.DAY_OF_MONTH, 1)
    }

    val daysInMonth = calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val firstDayOffset = calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
    val today = java.util.Calendar.getInstance()

    Column(modifier = Modifier.background(Color.LightGray.copy(0.1f), RoundedCornerShape(12.dp)).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        for (row in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = (row * 7 + col) - firstDayOffset + 1
                    val dateString = "$displayYear-$displayMonth-$dayNum"

                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { if (dayNum in 1..daysInMonth) onDateLongClick(dateString) }
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val isToday = monthOffset == 0 && dayNum == today.get(java.util.Calendar.DAY_OF_MONTH)
                            val isNatHoliday = getNationalHoliday(displayMonth, dayNum) != null
                            val isDbHoliday = globalEvents.any { it["date"] == dateString }
                            val checkCal = java.util.Calendar.getInstance().apply { set(displayYear, displayMonth - 1, dayNum) }
                            val isWeekend = checkCal.get(java.util.Calendar.DAY_OF_WEEK).let { it == 1 || it == 7 }

                            val isRedDay = isNatHoliday || isDbHoliday || isWeekend
                            val hasPersonalEvent = personalEvents.any { it["date"] == dateString }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isToday -> Color.Black
                                        isRedDay -> Color(0xFFF44336)
                                        else -> Color.Transparent
                                    }
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isToday || isRedDay) Color.White else Color.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 14.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (hasPersonalEvent) {
                                    Box(Modifier.size(4.dp).background(if(isRedDay || isToday) Color.Cyan else Color.Blue, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun EventItemRow(event: Map<String, Any>, canDelete: Boolean, db: FirebaseFirestore, collection: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(event["title"]?.toString() ?: "Event", fontWeight = FontWeight.Bold)
                Text(event["date"]?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
            }
            if (canDelete && collection.isNotEmpty()) {
                IconButton(onClick = { db.collection(collection).document(event["docId"].toString()).delete() }) {
                    Icon(Icons.Default.Delete, "Delete", tint = Color.LightGray)
                }
            }
        }
    }
}

// --- 4. PHONE DIRECTORY (Inside Academics) ---
@Composable
fun PhoneDirectory(isAdmin: Boolean = false) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val contacts = remember { mutableStateListOf<Contact>() }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    // Admin Input States
    var nName by remember { mutableStateOf("") }
    var nRole by remember { mutableStateOf("") }
    var nPhone by remember { mutableStateOf("") }
    var nEmail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        db.collection("directory").orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) { isLoading = false; return@addSnapshotListener }
                contacts.clear()
                snapshot?.documents?.forEach { doc ->
                    // copies the document ID into the contact object
                    val contact = doc.toObject<Contact>()?.copy(id = doc.id)
                    if (contact != null) contacts.add(contact)
                }
                isLoading = false
            }
    }

    val filteredContacts = contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.role.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isAdmin) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Admin: Add Contact", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = nName, onValueChange = { nName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nRole, onValueChange = { nRole = it }, label = { Text("Role") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nPhone, onValueChange = { nPhone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nEmail, onValueChange = { nEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (nName.isNotBlank()) {
                                val newContact = Contact(name = nName, role = nRole, phone = nPhone, email = nEmail)
                                db.collection("directory").add(newContact) // Firestore generates the ID here
                                    .addOnSuccessListener {
                                        nName = ""; nRole = ""; nPhone = ""; nEmail = ""
                                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                    ,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("Save to Directory") }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Search contact...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(filteredContacts) { contact ->
                    DirectoryContactCard(
                        contact = contact,
                        isAdmin = isAdmin,
                        onDelete = {
                            db.collection("directory").document(contact.id).delete()
                                .addOnSuccessListener { Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show() }
                        }
                    )
                }
            }
        }
    }
}

// --- 5. DIRECTORY CONTACT CARD ---
@Composable
fun DirectoryContactCard(contact: Contact, isAdmin: Boolean = false, onDelete: () -> Unit = {}) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = contact.role, fontSize = 13.sp, color = Color.Gray)
                if (contact.email.isNotEmpty()) {
                    Text(text = contact.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (contact.email.isNotEmpty()) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("mailto:${contact.email}") }
                        context.startActivity(intent)
                    }) { Icon(Icons.Default.Email, "Email", tint = Color(0xFF2196F3)) }
                }

                if (contact.phone.isNotEmpty()) {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:${contact.phone}") }
                        context.startActivity(intent)
                    }) { Icon(Icons.Default.Call, "Call", tint = Color(0xFF4CAF50)) }
                }

                // THE DELETE BUTTON
                if (isAdmin) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}


@Composable
fun HostelDirectory(isAdmin: Boolean = false) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val contacts = remember { mutableStateListOf<Contact>() }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    var nName by remember { mutableStateOf("") }
    var nRole by remember { mutableStateOf("") }
    var nPhone by remember { mutableStateOf("") }
    var nEmail by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Correct collection for listening
        db.collection("hostel_directory").orderBy("name")
            .addSnapshotListener { snapshot, _ ->
                contacts.clear()
                snapshot?.documents?.forEach { doc ->
                    val contact = doc.toObject<Contact>()?.copy(id = doc.id)
                    if (contact != null) contacts.add(contact)
                }
                isLoading = false
            }
    }

    val filteredContacts = contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.role.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isAdmin) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Admin: Add Staff", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    OutlinedTextField(value = nName, onValueChange = { nName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nRole, onValueChange = { nRole = it }, label = { Text("Role") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nPhone, onValueChange = { nPhone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nEmail, onValueChange = { nEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            if (nName.isNotBlank()) {
                                val newContact = Contact(name = nName, role = nRole, phone = nPhone, email = nEmail)
                                // FIXED: Changed "directory" to "hostel_directory"
                                db.collection("hostel_directory").add(newContact)
                                    .addOnSuccessListener {
                                        nName = ""; nRole = ""; nPhone = ""; nEmail = ""
                                        Toast.makeText(context, "Saved to Hostel List!", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) { Text("Add Staff") }
                }
            }
        }

        OutlinedTextField(
            value = searchQuery, onValueChange = { searchQuery = it },
            placeholder = { Text("Search staff...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(filteredContacts) { contact ->
                    DirectoryContactCard(
                        contact = contact,
                        isAdmin = isAdmin,
                        onDelete = {
                            // Correct collection for deleting
                            db.collection("hostel_directory").document(contact.id).delete()
                                .addOnSuccessListener { Toast.makeText(context, "Removed", Toast.LENGTH_SHORT).show() }
                        }
                    )
                }
            }
        }
    }
}