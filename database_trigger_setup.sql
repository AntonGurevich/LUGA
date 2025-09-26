-- Solution 3: Database Trigger for Email Confirmation
-- This trigger sends confirmation email only after successful user_registry insert

-- Step 1: Create a function to send confirmation email
CREATE OR REPLACE FUNCTION send_user_confirmation_email()
RETURNS TRIGGER AS $$
BEGIN
    -- Send confirmation email to the user
    -- This uses Supabase's built-in email function
    PERFORM auth.send_confirmation_email(NEW.email);
    
    -- Log the action
    RAISE NOTICE 'Confirmation email sent to: %', NEW.email;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Step 2: Create the trigger that fires after INSERT on user_registry
CREATE OR REPLACE TRIGGER trigger_send_confirmation_email
    AFTER INSERT ON user_registry
    FOR EACH ROW
    EXECUTE FUNCTION send_user_confirmation_email();

-- Step 3: Grant necessary permissions
-- Make sure the function can send emails
GRANT EXECUTE ON FUNCTION send_user_confirmation_email() TO authenticated;
GRANT EXECUTE ON FUNCTION send_user_confirmation_email() TO anon;

-- Alternative approach using Supabase Edge Functions (RECOMMENDED)
-- This approach is more reliable and gives you better control

-- Step 1: Enable the http extension (if not already enabled)
-- CREATE EXTENSION IF NOT EXISTS http;

-- Step 2: Create function to call Edge Function
CREATE OR REPLACE FUNCTION send_user_confirmation_email_via_edge()
RETURNS TRIGGER AS $$
DECLARE
    response http_response;
    project_url text;
BEGIN
    -- Get your project URL (replace with your actual project URL)
    project_url := 'https://your-project-id.supabase.co';
    
    -- Call the Edge Function to send confirmation email
    SELECT * INTO response FROM http_post(
        project_url || '/functions/v1/send-confirmation-email',
        json_build_object(
            'email', NEW.email,
            'user_id', NEW.uid
        )::text,
        'application/json',
        '{"Authorization": "Bearer ' || current_setting('app.settings.service_role_key', true) || '"}'
    );
    
    -- Log the response
    RAISE NOTICE 'Edge Function response: %', response.content;
    
    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        -- Log any errors but don't fail the insert
        RAISE WARNING 'Failed to send confirmation email: %', SQLERRM;
        RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Step 3: Create trigger using Edge Function approach
DROP TRIGGER IF EXISTS trigger_send_confirmation_email ON user_registry;
CREATE TRIGGER trigger_send_confirmation_email_via_edge
    AFTER INSERT ON user_registry
    FOR EACH ROW
    EXECUTE FUNCTION send_user_confirmation_email_via_edge();

-- Step 4: Test the trigger
-- You can test it by inserting a user:
-- INSERT INTO user_registry (email, connection_code, registration_date) 
-- VALUES ('test@example.com', 12345678, '2024-01-01');

-- Step 5: Monitor the trigger
-- Check if emails are being sent in Supabase Dashboard > Authentication > Users
-- Or check the logs in Supabase Dashboard > Logs
