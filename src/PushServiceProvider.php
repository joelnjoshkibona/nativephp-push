<?php

namespace Blutrixx\Push;

use Illuminate\Support\ServiceProvider;

/**
 * No PHP of its own: the web view calls the bridge functions straight from the page (POST /_native/api/call, see
 * resources/js/usePush.ts) and registers the token with the app's own API.
 */
class PushServiceProvider extends ServiceProvider
{
    public function register(): void
    {
        //
    }

    public function boot(): void
    {
        //
    }
}
