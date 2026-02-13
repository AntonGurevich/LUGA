# Supabase Email Verification Error - Backend Developer Report

## Error Summary

**Error Message**: `Error sending confirmation email`  
**Exception Type**: `io.github.jan.supabase.gotrue.exception.AuthRestException`  
**Occurrence**: During user registration when Supabase attempts to send email verification

## What This Error Means

This error indicates that Supabase's Auth service is unable to send the confirmation email to the user during registration. The user account is created in Supabase Auth, but the email verification cannot be delivered.

## Exact Error Information Extracted

Based on the logs, here's what we know:

```
Exception Type: io.github.jan.supabase.gotrue.exception.AuthRestException
Error Message: Error sending confirmation email
Exception Cause: No cause
```

## Additional Information Being Logged

The app now extracts and logs the following details (check Logcat for "=== FULL ERROR DETAILS FOR BACKEND DEV ==="):

1. **HTTP Status Code** (if available in exception)
2. **Error Code** (Supabase-specific error code, if available)
3. **Error Description** (detailed error description, if available)
4. **All Exception Fields** (any additional fields from AuthRestException)

## What to Check in Supabase Dashboard

### 1. Email Service Configuration
**Location**: Dashboard → Settings → Auth → SMTP Settings

**Check**:
- [ ] Is SMTP configured? (If using custom SMTP)
- [ ] Are SMTP credentials valid?
- [ ] Is the SMTP service accessible?

**If using Supabase's built-in email service**:
- [ ] Is email service enabled for your project?
- [ ] Check project tier (free tier has email limits)

### 2. Email Provider Status
**Location**: Dashboard → Authentication → Providers → Email

**Check**:
- [ ] Email provider is enabled
- [ ] "Confirm email" setting is enabled (this is what triggers the email)
- [ ] Email templates are configured

### 3. Email Rate Limits
**Location**: Dashboard → Settings → Usage

**Check**:
- [ ] Current email usage vs. limits
- [ ] Free tier projects: 4 emails/hour limit
- [ ] Pro tier: Higher limits

### 4. Auth Logs
**Location**: Dashboard → Logs → Auth Logs

**Check**:
- [ ] Filter for email-related errors
- [ ] Look for specific error codes or messages
- [ ] Check timestamp of errors

### 5. Email Templates
**Location**: Dashboard → Authentication → Email Templates

**Check**:
- [ ] Confirmation email template exists
- [ ] Template is not malformed
- [ ] Template contains `{{ .ConfirmationURL }}`

## Common Causes and Solutions

### Cause 1: Email Service Not Configured
**Symptom**: No SMTP settings or built-in email service disabled  
**Solution**: Configure SMTP in Settings → Auth → SMTP Settings OR ensure built-in email is enabled

### Cause 2: Email Rate Limit Exceeded
**Symptom**: Free tier projects with high email volume  
**Solution**: Wait for rate limit reset OR upgrade to Pro tier

### Cause 3: Invalid Email Configuration
**Symptom**: SMTP credentials incorrect or service unavailable  
**Solution**: Verify SMTP settings and test connection

### Cause 4: Email Template Issues
**Symptom**: Template is malformed or missing required variables  
**Solution**: Check email templates in Dashboard → Authentication → Email Templates

## Next Steps

1. **Run the app again** and check Logcat for the enhanced error logs
2. Look for log entry: `=== FULL ERROR DETAILS FOR BACKEND DEV ===`
3. Share the complete error details with the backend team
4. Check Supabase Dashboard → Logs → Auth Logs for server-side error details
5. Verify email service configuration in Supabase Dashboard

## Expected Log Output

When you run the app and the error occurs, you should see:

```
AuthManager: === FULL ERROR DETAILS FOR BACKEND DEV ===
[All extracted exception fields and properties]
[HTTP Status Code if available]
[Error Code if available]
[Error Description if available]
[All other exception fields]

AuthManager: === BACKEND DEV ERROR REPORT ===
[Formatted error report with actionable items]
```

## Additional Resources

- **Supabase Auth Email Docs**: https://supabase.com/docs/guides/auth/auth-email
- **Supabase SMTP Configuration**: https://supabase.com/docs/guides/auth/auth-smtp
- **Supabase Error Codes**: Check Supabase documentation for specific error codes

## Important Note

The error message "Error sending confirmation email" is the generic message from Supabase. The actual root cause is likely one of:
1. Email service configuration issue
2. Rate limiting
3. SMTP connection problem
4. Email template issue

The backend developer should check the Supabase Dashboard logs for more specific error details, as the server-side logs will have the actual HTTP response and error codes from Supabase's email service.




