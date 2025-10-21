# Supabase Functions Module Setup

## Problem Fixed

The error `Unresolved reference 'functions'` was caused by missing the Supabase Functions module in the client configuration.

## Changes Made

### 1. Updated SupabaseClient.kt

**Added import:**
```kotlin
import io.github.jan.supabase.functions.Functions
```

**Added Functions module to client:**
```kotlin
val client = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_ANON_KEY
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
    install(Storage)
    install(Functions) // ← Added this
}
```

### 2. Updated build.gradle

**Added Functions dependency:**
```gradle
implementation 'io.github.jan-tennert.supabase:functions-kt'
```

## What This Enables

Now you can use Edge Functions in your Android app:

```kotlin
// Call Edge Function
val response = supabase.functions.invoke(
    function = "delete-user-account",
    parameters = mapOf("user_id" to userId)
)
```

## Next Steps

1. **Sync Project**: In Android Studio, click "Sync Project with Gradle Files"
2. **Deploy Edge Function**: Make sure your `delete-user-account` Edge Function is deployed
3. **Test**: The compilation error should now be resolved

## Available Edge Functions

Based on your setup, you have these Edge Functions:

1. **delete-user-account**: Deletes auth users securely
2. **send-confirmation-email**: Sends confirmation emails after database insert

## Testing

### Test Functions Module
```kotlin
// This should now compile without errors
supabase.functions.invoke(
    function = "test-function",
    parameters = mapOf("test" to "value")
)
```

### Test User Deletion
```kotlin
// This should now work
authManager.deleteCurrentUser { result ->
    // Handle result
}
```

## Troubleshooting

### If you still get compilation errors:

1. **Clean and Rebuild**:
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

2. **Invalidate Caches**:
   - Android Studio → File → Invalidate Caches and Restart

3. **Check Dependencies**:
   - Ensure all Supabase dependencies are using the same BOM version
   - Check for any conflicting versions

### If Edge Function calls fail:

1. **Check Function Deployment**:
   ```bash
   supabase functions list
   ```

2. **Check Function Logs**:
   ```bash
   supabase functions logs delete-user-account
   ```

3. **Verify Function URL**:
   - Make sure the function name matches exactly
   - Check your project URL is correct

## Benefits

✅ **Secure User Deletion**: Can now delete auth users server-side
✅ **Email Confirmation**: Can send emails after successful database operations
✅ **Server-Side Logic**: Can implement complex business logic in Edge Functions
✅ **Better Security**: Service role keys stay on the server

The compilation error should now be resolved! 🎉



















