# Custom SMTP Server Troubleshooting Guide

## Error: "Error sending confirmation email" with Custom SMTP

You're using a custom SMTP server registered in Supabase. This error indicates that Supabase cannot send emails through your SMTP server. Here's how to diagnose and fix it.

## Quick Diagnostic Checklist

### 1. Verify SMTP Configuration in Supabase Dashboard

**Location**: Dashboard → Settings → Auth → SMTP Settings

**Check the following:**
- [ ] **SMTP Host**: Correct hostname/IP address
- [ ] **SMTP Port**: Correct port (usually 587 for TLS, 465 for SSL, 25 for unsecured)
- [ ] **SMTP Username**: Correct username/email
- [ ] **SMTP Password**: Correct password (may need app-specific password)
- [ ] **Sender Email**: Matches SMTP authentication email
- [ ] **Sender Name**: Configured correctly
- [ ] **Security**: TLS/SSL settings match your SMTP server requirements

### 2. Test SMTP Connection

**In Supabase Dashboard**:
1. Go to Settings → Auth → SMTP Settings
2. Click **"Test SMTP Connection"** or **"Send Test Email"**
3. Check if test email is sent successfully
4. **If test fails, the error message will show the exact issue**

### 3. Common SMTP Issues

#### Issue 1: SMTP Authentication Failed
**Symptom**: Error about authentication or credentials
**Solution**:
- Verify username and password are correct
- For Gmail: Use App Password (not regular password)
- For Outlook/Office365: May need app-specific password
- Check if SMTP server requires special authentication method
- Verify the email account is not locked or suspended

#### Issue 2: SMTP Connection Refused
**Symptom**: Connection timeout or refused errors
**Solution**:
- Verify SMTP host and port are correct
- Check if SMTP server allows connections from Supabase IP addresses
- Verify firewall rules allow SMTP connections
- Check if SMTP server requires IP whitelisting
- Test SMTP connection from another location/tool

#### Issue 3: TLS/SSL Configuration
**Symptom**: SSL/TLS handshake errors
**Solution**:
- Verify TLS/SSL settings match your SMTP server
- Port 587 usually requires STARTTLS
- Port 465 usually requires SSL/TLS
- Check if server certificate is valid
- Some servers require specific TLS versions

#### Issue 4: Sender Email Mismatch
**Symptom**: Sender email not matching SMTP authentication
**Solution**:
- Verify sender email matches SMTP username
- Some SMTP servers don't allow sending from different addresses
- Check if SMTP server requires sender verification

#### Issue 5: SMTP Server Rate Limits
**Symptom**: Works sometimes but fails after multiple attempts
**Solution**:
- Check SMTP server rate limits
- Some providers limit emails per hour/day
- Consider using a dedicated email service for production

#### Issue 6: SMTP Server Blocking
**Symptom**: Connections work but emails are rejected
**Solution**:
- Check SMTP server logs for rejection reasons
- Verify SPF/DKIM records are configured
- Check if sender domain is blacklisted
- Verify email content doesn't trigger spam filters

### 4. Check Supabase Auth Logs

**Location**: Dashboard → Logs → Auth Logs

**Look for**:
- SMTP connection errors
- Authentication failures
- Email sending failures
- Specific error codes or messages

**Filter by**:
- Time range when error occurred
- Error type: "email" or "smtp"
- User email address

### 5. Verify SMTP Server Accessibility

Your SMTP server must be accessible from Supabase's servers. Check:

- [ ] **Firewall Rules**: Allow connections from Supabase IP ranges
- [ ] **IP Whitelisting**: If SMTP server requires IP whitelisting, add Supabase IPs
- [ ] **Network Security**: Check if corporate firewall blocks SMTP
- [ ] **Port Accessibility**: Verify SMTP port is open and accessible

**Note**: Supabase servers need to connect to your SMTP server, so ensure it's publicly accessible (not behind a VPN or private network only).

### 6. Test SMTP Configuration Externally

Before testing in Supabase, verify SMTP works with a tool like:

- **Telnet**: `telnet smtp.yourserver.com 587`
- **SMTP Test Tools**: Online SMTP testers
- **Email Clients**: Configure email client with same SMTP settings

If external tests fail, the issue is with your SMTP server configuration, not Supabase.

### 7. Common SMTP Providers Configuration

#### Gmail SMTP
```
Host: smtp.gmail.com
Port: 587 (TLS) or 465 (SSL)
Username: your-email@gmail.com
Password: App Password (NOT regular password)
Security: TLS (for port 587) or SSL (for port 465)
```
**Important**: Must use App Password, not regular Gmail password.

#### Outlook/Office365 SMTP
```
Host: smtp.office365.com
Port: 587
Username: your-email@outlook.com
Password: App Password or regular password
Security: STARTTLS
```
**Important**: May need to enable "Less secure app access" or use App Password.

#### SendGrid SMTP
```
Host: smtp.sendgrid.net
Port: 587
Username: apikey
Password: Your SendGrid API Key
Security: TLS
```

#### AWS SES SMTP
```
Host: email-smtp.[region].amazonaws.com
Port: 587 or 465
Username: SMTP username
Password: SMTP password
Security: TLS or SSL
```

### 8. Check Email Templates

**Location**: Dashboard → Authentication → Email Templates

**Verify**:
- [ ] Confirmation email template exists
- [ ] Template is not malformed
- [ ] Sender email in template matches SMTP configuration
- [ ] Template variables are correct (`{{ .ConfirmationURL }}`)

### 9. Debugging Steps

1. **Enable Detailed Logging**:
   - Check Supabase Dashboard → Logs → Auth Logs
   - Filter for email-related errors
   - Look for specific SMTP error messages

2. **Test with Simple Email**:
   - Try sending a test email from Supabase Dashboard
   - If test fails, you'll see the exact SMTP error
   - This helps isolate the issue

3. **Verify SMTP Credentials**:
   - Double-check username and password
   - Try logging in with email client using same credentials
   - If email client fails, SMTP credentials are wrong

4. **Check SMTP Server Status**:
   - Verify SMTP server is running and accessible
   - Check SMTP server logs for connection attempts
   - Look for blocked or failed connections from Supabase IPs

### 10. Common Error Messages and Solutions

| Error Message | Likely Cause | Solution |
|--------------|--------------|----------|
| "Authentication failed" | Wrong credentials | Verify username/password, use App Password if needed |
| "Connection refused" | SMTP server not accessible | Check firewall, verify host/port, test connectivity |
| "TLS handshake failed" | SSL/TLS configuration | Verify TLS/SSL settings match SMTP server |
| "Sender email rejected" | Email mismatch | Verify sender email matches SMTP username |
| "Rate limit exceeded" | Too many emails | Check SMTP server rate limits, wait and retry |
| "Domain not verified" | SPF/DKIM issues | Configure DNS records for sender domain |

## Action Items for Backend Developer

1. **Verify SMTP Configuration**:
   - Go to Supabase Dashboard → Settings → Auth → SMTP Settings
   - Review all SMTP settings
   - Click "Test SMTP Connection" and check the result

2. **Check Supabase Logs**:
   - Dashboard → Logs → Auth Logs
   - Filter for errors around the time of registration
   - Look for SMTP-specific error messages

3. **Test SMTP Externally**:
   - Use email client or SMTP test tool
   - Verify SMTP server is accessible and credentials work
   - If external test fails, fix SMTP server configuration first

4. **Verify Network Access**:
   - Ensure SMTP server is publicly accessible
   - Check firewall rules allow Supabase IPs
   - Verify SMTP port is open

5. **Check SMTP Server Logs**:
   - Review SMTP server logs for connection attempts
   - Look for authentication failures or connection rejections
   - Verify Supabase servers can reach your SMTP server

## Next Steps

After fixing SMTP configuration:

1. **Test in Supabase Dashboard**:
   - Send test email from SMTP settings
   - Verify email is received

2. **Test Registration Flow**:
   - Register a new user in the app
   - Verify confirmation email is sent
   - Check email is received

3. **Monitor Logs**:
   - Watch Auth Logs for any SMTP errors
   - Verify emails are being sent successfully

## Additional Resources

- **Supabase SMTP Docs**: https://supabase.com/docs/guides/auth/auth-smtp
- **Supabase Auth Email**: https://supabase.com/docs/guides/auth/auth-email
- **Gmail App Passwords**: https://support.google.com/accounts/answer/185833
- **Office365 SMTP**: https://docs.microsoft.com/en-us/exchange/mail-flow-best-practices/how-to-set-up-a-multifunction-device-or-application-to-send-email-using-microsoft-365-or-office-365

## Important Notes

- **App Passwords**: Many email providers (Gmail, Outlook) require App Passwords for SMTP, not regular passwords
- **IP Whitelisting**: Some SMTP servers require whitelisting Supabase IP addresses
- **Rate Limits**: Check both Supabase and SMTP server rate limits
- **Security**: Use TLS/SSL for SMTP connections (never use unencrypted port 25)




