// Supabase Edge Function to send confirmation email
// This function is called by the database trigger after successful user_registry insert

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    // Create Supabase client
    const supabaseClient = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? ''
    )

    // Parse request body
    const { email, user_id } = await req.json()

    if (!email || !user_id) {
      return new Response(
        JSON.stringify({ error: 'Missing email or user_id' }),
        { 
          status: 400, 
          headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
        }
      )
    }

    // Send confirmation email using Supabase Auth Admin API
    const { data, error } = await supabaseClient.auth.admin.generateLink({
      type: 'signup',
      email: email,
      options: {
        redirectTo: `${Deno.env.get('SITE_URL')}/auth/callback`
      }
    })

    if (error) {
      console.error('Error generating confirmation link:', error)
      return new Response(
        JSON.stringify({ error: 'Failed to generate confirmation link' }),
        { 
          status: 500, 
          headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
        }
      )
    }

    // Send the email using your preferred email service
    // For now, we'll use Supabase's built-in email function
    const { error: emailError } = await supabaseClient.auth.admin.inviteUserByEmail(email, {
      redirectTo: `${Deno.env.get('SITE_URL')}/auth/callback`
    })

    if (emailError) {
      console.error('Error sending confirmation email:', emailError)
      return new Response(
        JSON.stringify({ error: 'Failed to send confirmation email' }),
        { 
          status: 500, 
          headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
        }
      )
    }

    console.log(`Confirmation email sent to: ${email}`)

    return new Response(
      JSON.stringify({ 
        success: true, 
        message: 'Confirmation email sent successfully',
        email: email 
      }),
      { 
        status: 200, 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
      }
    )

  } catch (error) {
    console.error('Unexpected error:', error)
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { 
        status: 500, 
        headers: { ...corsHeaders, 'Content-Type': 'application/json' } 
      }
    )
  }
})

