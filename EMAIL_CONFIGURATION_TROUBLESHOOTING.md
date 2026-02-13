# Email Configuration Troubleshooting Guide

## Error: "Error sending confirmation email"

This error indicates that Supabase is unable to send the confirmation email. Here's how to diagnose and fix it.

## Diagnostic Steps

### 1. Check Supabase Dashboard Email Configuration

Go to your Supabase Dashboard → **Authentication** → **Providers** → **Email**

**Check the following:**
- [ ] Email provider is enabled
- [ ] "Confirm email" option is enabled (this is what we want)
- [ ] Email templates are configured

### 2. Check Email Service Configuration

**Option A: Using Supabase Built-in Email (Default)**
- Supabase provides email service by default on hosted projects
- Check if your project is on a free tier (may have email limits)
- Check Dashboard → Settings → Billing for email quotas

**Option B: Using Custom SMTP**
- Go to Dashboard → Settings → Auth → SMTP Settings
- Verify SMTP configuration:
  - [ ] SMTP host
  - [ ] SMTP port
  - [ ] SMTP username
  - [ ] SMTP password
  - [ ] Sender email
  - [ ] Test email sending

### 3. Check Email Rate Limits

Free tier projects have email rate limits:
- **Free tier**: Limited emails per hour/day
- **Pro tier**: Higher limits
- Check Dashboard → Settings → Usage for email usage

### 4. Check Redirect URLs

Go to Dashboard → **Authentication** → **URL Configuration**

**Required Settings:**
- [ ] Site URL is configured
- [ ] Redirect URLs include your app's deep link scheme
- [ ] Add: `acteamity://auth` (or your custom scheme)

### 5. Verify Email Templates

Go to Dashboard → **Authentication** → **Email Templates**

**Check:**
- [ ] Confirmation email template exists
- [ ] Template contains `{{ .ConfirmationURL }}` variable
- [ ] Email template is not malformed

## Common Issues and Solutions

### Issue 1: Email Service Not Configured
**Symptom**: "Error sending confirmation email"
**Solution**: 
1. Check if SMTP is configured (Settings → Auth → SMTP)
2. If not using custom SMTP, ensure Supabase email service is enabled
3. Free tier projects should have email enabled by default

### Issue 2: Email Rate Limit Exceeded
**Symptom**: "Error sending confirmation email" after multiple attempts
**Solution**:
1. Wait for rate limit to reset (usually 1 hour)
2. Upgrade to Pro tier for higher limits
3. Implement email retry with backoff

### Issue 3: Invalid Email Address
**Symptom**: "Error sending confirmation email" for specific emails
**Solution**:
1. Verify email format is valid
2. Check if email domain is blacklisted
3. Try with a different email address

### Issue 4: Network/Firewall Issues
**Symptom**: Intermittent email sending failures
**Solution**:
1. Check network connectivity
2. Verify firewall isn't blocking SMTP ports
3. Check Supabase service status

### Issue 5: Missing Redirect URL Configuration
**Symptom**: Email sends but link doesn't work
**Solution**:
1. Configure redirect URLs in Dashboard
2. Add your deep link scheme: `acteamity://auth`
3. Verify Site URL is set correctly

## Testing Email Configuration

### Test 1: Send Test Email from Dashboard
1. Go to Dashboard → Authentication → Users
2. Click "Send magic link" or "Send confirmation email"
3. Check if email is received

### Test 2: Check Email Logs
1. Go to Dashboard → Logs → Auth Logs
2. Filter for "email" events
3. Look for error messages related to email sending

### Test 3: Verify SMTP Connection (if using custom SMTP)
1. Go to Dashboard → Settings → Auth → SMTP Settings
2. Click "Test SMTP Connection"
3. Verify test email is received

## Enhanced Logging

The app now includes enhanced error logging. When you see the error, check Logcat for:

```
AuthManager: Registration error: [error message]
AuthManager: Exception type: [exception class]
AuthManager: Exception cause: [cause message]
AuthManager: Detailed error info: [detailed breakdown]
```

This will help identify the specific issue.

## Quick Fixes

### Quick Fix 1: Disable Email Verification (Testing Only)
**For testing purposes only:**
1. Go to Dashboard → Authentication → Providers → Email
2. Disable "Confirm email"
3. **Warning**: This reduces security - only for testing!

### Quick Fix 2: Use Custom SMTP
If Supabase built-in email is having issues:
1. Set up custom SMTP (Gmail, SendGrid, etc.)
2. Configure in Dashboard → Settings → Auth → SMTP Settings
3. Test email sending

### Quick Fix 3: Check Project Status
1. Verify your Supabase project is active
2. Check if project is paused or has billing issues
3. Ensure you're using the correct project

## Next Steps After Fixing

Once email is configured correctly:

1. **Test Registration Flow**:
   - Register a new user
   - Verify email is received
   - Click verification link
   - Verify deep link opens app
   - Complete registration

2. **Monitor Email Delivery**:
   - Check spam folders
   - Verify email template renders correctly
   - Test with different email providers

3. **Set Up Monitoring**:
   - Monitor email failure rates
   - Set up alerts for email service issues
   - Track email delivery success rates

## Support Resources

- **Supabase Docs**: https://supabase.com/docs/guides/auth/auth-email
- **Supabase Discord**: Community support
- **Supabase Dashboard**: Check service status and logs
- **Email Service Status**: Check if there are known issues

## Additional Notes

- Free tier projects may have email sending limitations
- Custom domains may require additional configuration
- Some email providers may mark Supabase emails as spam
- Always test with multiple email providers (Gmail, Outlook, etc.)

