******CampusConnect IIITG******
CampusConnect IIITG is a comprehensive digital ecosystem designed to unify the student experience and streamline campus administration at IIIT Guwahati. Developed to bridge the gap between fragmented campus services, the application provides a centralized, secure, and AI-enhanced platform for academics, hostel management, and student welfare. By integrating modern software architecture with generative AI, it transforms how students interact with their campus environment, making daily activities more efficient and transparent.




******Security & API Integrity******


Security is the core foundation of CampusConnect IIITG. The app employs multiple layers of protection to ensure data privacy and system integrity:

Production-Grade API Security: To prevent unauthorized access and data leaks, the project utilizes a secure configuration where the Gemini API Key is stored in a local local.properties file.

BuildConfig Integration: Keys are accessed via the BuildConfig class, ensuring that sensitive credentials are never hardcoded in the source code or exposed in the public version control history.


Hardware-Level Authentication: The application integrates the Google Biometric Prompt API, requiring a fingerprint or face unlock to access the app or secure sessions.



Domain-Locked Access: Registration is strictly limited to verified @iiitg.ac.in email addresses or whitelisted administrator accounts.


******Google Technologies & Core Features******


Google Technologies Integrated

Gemini 2.5 Flash: Powers the generative AI core for academic planning.

Firebase Authentication: Provides secure, domain-restricted user sign-in.

Cloud Firestore: A real-time NoSQL database for grievances, academic data, and admin controls.

Jetpack Compose: Used for building the modern, declarative native UI.

Google AI SDK for Android: Facilitates direct on-device communication with Gemini models.

Other Major Features

Real-time Synchronization: Instant updates for mess menus and grievance statuses.

Material Design 3: A polished, responsive interface following the latest Google design standards.

Searchable Directories: High-speed lookup for campus and hostel staff contacts.






******Project Structure******





Plaintext

app/
 ├── src/main/java/com/campusconnect/iiitg/
 │    ├── MainActivity.kt        # Application Entry & Navigation Router
 │    ├── AppConfig.kt           # Configuration & App-wide Constants
 │    └── utils/
 │         └── BiometricHelper.kt # Biometric Prompt Implementation
 ├── build.gradle.kts            # Dependency Management & Build Config
local.properties   
# (PRIVATE) Local storage for Gemini API Keys




******Module Breakdown******
1. Dashboard (The Command Center)
The main entry point providing a grid-based navigation system to all campus services .


Student View: Can navigate to all utility modules and lock the app via the "Secure App Session" toggle.

Admin View: Gains additional visibility into hidden whitelisted management tools.

2. Academic Hub
A multi-tab system focused on student success.

Study AI: Powered by Gemini 2.5 Flash to generate custom study plans.

GPA Calc: A goal-setting tool to calculate required grades for target CPIs.

Attendance & Courses: Personal trackers for managing course loads and maintaining the 75% attendance requirement.

Directory: Searchable list of faculty and academic staff.

Admin Mode: Can add, edit, or remove contacts from the directory.

3. Grievance Portal
A direct channel for reporting campus issues.

Sub-Tabs: Categories for Boy's Hostel, Girl's Hostel, SAC, and Academic Block .

Status Tab: Shows a live ticket history (Pending, In Progress, Resolved).

Student View: Can submit new grievances and track their own status.

Admin View: Can view all campus complaints and update their resolution status.

4. Hostel Hub

Centralizes hostel-related administrative tasks.

Leave Portal: Digitized outstation leave applications with departure dates and parent contacts.

Directory: Quick access to Warden and Caretaker contact details.

Admin View: Can approve or reject student leave requests in real-time.

5. Mess Module

Manages the daily nutrition and billing cycles.

Menu Tab: View the breakfast, lunch, and dinner menu for any day of the week.

Apply Tab: Submit mess rebate requests for approved leaves.

Admin View: Can edit the daily menu for all students and manage rebate approvals.

6. Campus Calendar
   
A unified view of the institute's schedule.

Student View: Can view national holidays, personal events, and campus-wide fests.

Admin View: Can mark global holidays and institute-wide events for all users.

7. Links Tab

Users can access various important links and INTRANET Portal via this tab.










******CREDITS******

TEAM LEADER :- ARIHANT PANDEY

TEAM MEMBERS :- 

1. ARYAN SHARMA
2. BIBEK KUMAR PEDENTI
3. DIYA AHUJA



Developed with ❤️ for IIIT Guwahati





















