# Resend SMTP Configuration Check

## Your Current Configuration

- **SMTP Host**: `smtp.resend.com`
- **Port**: `465`
- **Username**: `resend`
- **Sender Email**: `mfa@resend.dev`
- **Sender Name**: `Acteamity MFA`

## ✅ Code Status: NO CHANGES NEEDED

Your Android app code does **NOT** need any changes. SMTP configuration is entirely handled by Supabase backend. Your app just calls:
- `supabase.auth.signUpWith(Email)` - which triggers Supabase to send email via your SMTP
- The SMTP details are only used by Supabase's backend, not your Android app

## ⚠️ Potential Resend SMTP Configuration Issues

### Issue 1: Port 465 Security Setting
**Your Setting**: Port `465`
**Requirement**: Port 465 requires **SSL** (not TLS/STARTTLS)

**Check in Supabase Dashboard**:
- Go to Settings → Auth → SMTP Settings
- Verify security setting is set to **SSL** (not TLS)
- Port 465 = SSL
- Port 587 = TLS/STARTTLS

### Issue 2: Resend SMTP Authentication
**Your Setting**: Username = `resend`

**Resend SMTP Requirements**:
- Username should be: `resend`
- Password should be: Your **Resend API Key** (not a regular password)
- The API key format is usually: `re_xxxxxxxxxxxxxxxxxxxxx`

**Action Required**:
1. Verify you're using your Resend API key as the password (not a regular password)
2. Get your API key from: Resend Dashboard → API Keys
3. Make sure the API key has sending permissions

### Issue 3: Sender Domain Verification
**Your Setting**: Sender email = `mfa@resend.dev`

**Resend Requirements**:
- The domain `resend.dev` must be verified in your Resend account
- OR use your own verified domain

**Check**:
1. Go to Resend Dashboard → Domains
2. Verify `resend.dev` is listed and verified
3. OR verify your own domain if using custom domain

**Important**: If using `resend.dev`, this is Resend's default domain. Make sure:
- Your Resend account allows using `resend.dev`
- The domain is verified in your Resend account
- OR use your own verified domain instead

### Issue 4: Resend Rate Limits
**Check**:
- Resend has rate limits on free/paid plans
- Verify your Resend plan allows the email volume you need
- Check Resend Dashboard → Usage for current limits

## 🔍 Diagnostic Steps

### Step 1: Verify Resend API Key
1. Go to Resend Dashboard → API Keys
2. Verify you have an active API key
3. Copy the API key
4. In Supabase Dashboard → Settings → Auth → SMTP Settings
5. Verify the Password field contains your Resend API key (not a regular password)

### Step 2: Check Security Setting for Port 465
1. In Supabase Dashboard → Settings → Auth → SMTP Settings
2. Verify security is set to **SSL** (for port 465)
3. If it says TLS or STARTTLS, change it to SSL

### Step 3: Test SMTP Connection in Supabase
1. In Supabase Dashboard → Settings → Auth → SMTP Settings
2. Click "Test SMTP Connection" or "Send Test Email"
3. **This will show the exact error** if SMTP is misconfigured
4. Common errors:
   - "Authentication failed" → Wrong API key or username
   - "Connection refused" → Wrong port or security setting
   - "SSL handshake failed" → Security setting mismatch

### Step 4: Verify Domain in Resend
1. Go to Resend Dashboard → Domains
2. Check if `resend.dev` is verified
3. If not, you may need to:
   - Use your own verified domain, OR
   - Verify `resend.dev` in your Resend account

### Step 5: Check Resend Logs
1. Go to Resend Dashboard → Logs
2. Check for failed email attempts
3. Look for error messages from Supabase connection attempts

## 🎯 Most Likely Issues (Based on Your Config)

### Most Likely Issue #1: Security Setting Mismatch
**Problem**: Port 465 requires SSL, but Supabase might be configured for TLS
**Solution**: 
1. In Supabase Dashboard → SMTP Settings
2. Verify Security is set to **SSL** (not TLS)
3. Port 465 = SSL
4. Port 587 = TLS

### Most Likely Issue #2: Wrong Password/API Key
**Problem**: Using regular password instead of Resend API key
**Solution**:
1. Get your Resend API key from Resend Dashboard
2. Update Supabase SMTP password with the API key
3. Format should be: `re_xxxxxxxxxxxxxxxxxxxxx`

### Most Likely Issue #3: Domain Not Verified
**Problem**: `resend.dev` domain not verified in Resend account
**Solution**:
1. Verify domain in Resend Dashboard
2. OR use your own verified domain for sender email

## ✅ Correct Resend SMTP Configuration

Based on Resend documentation, the correct settings should be:

```
Host: smtp.resend.com
Port: 465
Security: SSL (NOT TLS)
Username: resend
Password: re_xxxxxxxxxxxxxxxxxxxxx (Your Resend API Key)
Sender Email: mfa@resend.dev (or your verified domain)
Sender Name: Acteamity MFA
```

## 🚨 Action Items

1. **Test SMTP in Supabase Dashboard** (MOST IMPORTANT)
   - This will show the exact error
   - Go to Settings → Auth → SMTP Settings → Test

2. **Verify Security Setting**
   - Port 465 = SSL (not TLS)
   - Verify this setting in Supabase Dashboard

3. **Verify API Key**
   - Ensure password field contains Resend API key
   - Format: `re_xxxxxxxxxxxxxxxxxxxxx`
   - Not a regular password

4. **Check Domain Verification**
   - Verify `resend.dev` in Resend Dashboard
   - OR use your own verified domain

5. **Check Supabase Logs**
   - Dashboard → Logs → Auth Logs
   - Look for SMTP-specific error messages

## 📝 Summary

**Your Android code is correct and doesn't need changes.**

The issue is with the **SMTP configuration in Supabase Dashboard**, specifically:
1. Security setting for port 465 (should be SSL)
2. Password field (should contain Resend API key, not regular password)
3. Domain verification in Resend

The error "Error sending confirmation email" is coming from Supabase's backend when it tries to connect to Resend SMTP. The test feature in Supabase Dashboard will show the exact error.




