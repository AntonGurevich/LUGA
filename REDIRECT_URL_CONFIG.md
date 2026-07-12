# Email Confirmation Redirect URL Configuration

The app expects email verification links to redirect to **https://acteamity.com/authentication**.

## Supabase Dashboard Configuration

1. Go to **Supabase Dashboard** → your project → **Authentication** → **URL Configuration**
2. Under **Redirect URLs**, add: `https://acteamity.com/authentication`
3. Ensure **Site URL** includes your production domain if needed

## acteamity.com/authentication Page

The page at https://acteamity.com/authentication must:

1. Receive the user after they click the verification link (Supabase redirects with tokens in the URL fragment)
2. Either:
   - **Option A**: Redirect to `acteamity://auth` + the URL fragment (e.g. `acteamity://auth#access_token=...`) so the Android app opens and can parse the session
   - **Option B**: Display a "Redirecting to app..." message and use JavaScript to open the app with the token fragment

## Android App

- The app has intent filters for both `acteamity://auth` and `https://acteamity.com/authentication`
- When the app receives either URL with session tokens, it parses and imports the session
- Password reset emails use `redirectUrl = "https://acteamity.com/authentication"`
