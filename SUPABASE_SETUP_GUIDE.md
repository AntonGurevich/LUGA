# Supabase Auth Setup Guide for Acteamity

This guide will help you complete the setup of Supabase authentication in your Acteamity Android app.

## Prerequisites

1. **Supabase Project**: You mentioned you already have a Supabase project with auth set up
2. **Android Studio**: Make sure you're using a recent version
3. **Kotlin**: The project is already configured for Kotlin

## Step 1: Update Supabase Configuration

1. Open `app/src/main/java/silverbackgarden/example/luga/SupabaseClient.kt`
2. Replace the placeholder values with your actual Supabase credentials:

```kotlin
// Replace these with your actual Supabase project credentials
private const val SUPABASE_URL = "https://your-project-ref.supabase.co"
private const val SUPABASE_ANON_KEY = "your-anon-key-here"
```

**To find your credentials:**
- Go to your Supabase dashboard
- Navigate to Settings > API
- Copy your Project URL and anon/public key

## Step 2: Sync Project Dependencies

1. In Android Studio, click **File > Sync Project with Gradle Files**
2. Or run: `./gradlew build` in the terminal
3. Wait for the sync to complete

## Step 3: Verify Auth Settings in Supabase

Make sure your Supabase project has the following auth settings:

1. **Email Auth**: Enabled (should be default)
2. **Email Confirmations**: You can disable for testing or enable for production
3. **Password Requirements**: Configure as needed

## Step 4: Test the Implementation

### Registration Flow:
1. Launch the app
2. If no user is logged in, you'll see the registration option
3. Fill in the registration form
4. User will be created in Supabase Auth
5. Navigate to login screen

### Login Flow:
1. Enter email and password
2. User will be authenticated with Supabase
3. Navigate to main app (CentralActivity)

## Step 5: Optional - Configure Email Templates

If you want custom email templates for confirmation/reset:

1. Go to Supabase Dashboard > Authentication > Email Templates
2. Customize the templates as needed

## Step 6: Optional - Add Password Reset

To add password reset functionality, you can extend the AuthManager:

```kotlin
fun resetPassword(email: String, callback: AuthCallback) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            supabase.auth.resetPasswordForEmail(email)
            withContext(Dispatchers.Main) {
                callback.onSuccess(null)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                callback.onError("Password reset failed: ${e.message}")
            }
        }
    }
}
```

## Features Implemented

✅ **User Registration**: Email/password registration with Supabase
✅ **User Login**: Email/password authentication
✅ **Session Management**: Automatic session persistence
✅ **Logout**: Proper session cleanup
✅ **Input Validation**: Email and password validation
✅ **Error Handling**: User-friendly error messages
✅ **Google Sign-In Integration**: Maintained existing Google Fit integration

## Migration Notes

The implementation maintains backward compatibility with your existing:
- SharedPreferences storage for additional user data
- Google Sign-In for fitness data access
- UI layouts and styling
- Navigation flow

## Troubleshooting

### Common Issues:

1. **Build Errors**: Run `./gradlew clean build` to resolve dependency issues
2. **Network Errors**: Ensure your Supabase URL and key are correct
3. **Auth Failures**: Check Supabase auth settings and email confirmation requirements

### Debug Tips:

1. Check the Android logs for detailed error messages
2. Verify Supabase dashboard for user creation
3. Test with a simple email/password combination first

## Next Steps

1. **Sync your project** with the new dependencies
2. **Update the SupabaseClient.kt** with your credentials
3. **Test the registration and login flows**
4. **Customize error messages** as needed for your app's UX

The authentication system is now fully integrated with Supabase and ready for production use!









