// GDPR account deletion: remove all app data and Auth user for the requesting user.
// Only the authenticated user (JWT) can delete their own account; UID is derived from JWT.
// Uses Supabase client getUser(jwt) for reliable user resolution (works after re-registration).

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const authHeader = req.headers.get("Authorization");
    if (!authHeader?.startsWith("Bearer ")) {
      return new Response(
        JSON.stringify({ error: "Missing or invalid Authorization header" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const supabaseAnonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
    const supabaseServiceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const jwt = authHeader.replace(/^Bearer\s+/i, "").trim();

    // Resolve user from JWT using the client API (more reliable than raw fetch to auth/v1/user)
    const supabaseAuth = createClient(supabaseUrl, supabaseAnonKey, {
      auth: { persistSession: false },
    });
    const {
      data: { user },
      error: userError,
    } = await supabaseAuth.auth.getUser(jwt);

    if (userError || !user?.id) {
      console.error("Auth getUser failed:", userError?.message ?? "no user");
      return new Response(
        JSON.stringify({ error: "Unauthorized or invalid token" }),
        { status: 401, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    const uid = user.id;

    const admin = createClient(supabaseUrl, supabaseServiceRoleKey, {
      auth: { persistSession: false },
    });

    // Delete app data (order: child tables first, then links, then registry)
    const tables = ["raw_steps", "raw_bike", "raw_swim", "user_corp_link", "users_registry"];
    for (const table of tables) {
      const { error } = await admin.from(table).delete().eq("uid", uid);
      if (error) {
        console.error(`Delete from ${table} failed:`, error);
      }
    }

    const { error: authError } = await admin.auth.admin.deleteUser(uid);
    if (authError) {
      console.error("Auth admin deleteUser failed:", authError);
      return new Response(
        JSON.stringify({ error: "Failed to delete account: " + authError.message }),
        { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
      );
    }

    return new Response(
      JSON.stringify({ success: true }),
      { status: 200, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  } catch (e) {
    console.error("delete-account error:", e);
    return new Response(
      JSON.stringify({ error: "Internal server error" }),
      { status: 500, headers: { ...corsHeaders, "Content-Type": "application/json" } }
    );
  }
});
