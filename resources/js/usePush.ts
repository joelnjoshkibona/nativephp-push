import { ref } from 'vue'

export type PushPermission = 'granted' | 'denied' | 'ask'

export interface PushStatus {
    permission: PushPermission
    /** The phone's Firebase token; empty until Firebase has given one. */
    token: string
}

export interface UsePushOptions {
    /** Registers the phone's token with your API (for example POST device-token-register). Throw to try again later. */
    register: (token: string) => Promise<void>
    /** localStorage key remembering the token last registered. Default: 'push_registered_token' */
    storageKey?: string
}

/**
 * Asks the app's native side for something (NativePHP's bridge, POST /_native/api/call on the app's own server) and
 * answers its data, or null: always in a browser, where there is no such server.
 */
async function nativeCall<T>(method: string, params: Record<string, unknown> = {}): Promise<T | null> {
    try {
        const res = await fetch('/_native/api/call', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
            body: JSON.stringify({ method, params }),
        })
        if (!res.ok) return null
        const body = (await res.json()) as { data?: T & { data?: T } }
        return body.data?.data ?? body.data ?? null
    } catch {
        return null
    }
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

const permission = ref<PushPermission | null>(null)

export function usePush(options: UsePushOptions) {
    const storageKey = options.storageKey ?? 'push_registered_token'

    function remembered(): string {
        try { return localStorage.getItem(storageKey) ?? '' } catch { return '' }
    }
    function remember(token: string) {
        try {
            if (token) localStorage.setItem(storageKey, token)
            else localStorage.removeItem(storageKey)
        } catch { /* private mode: it just registers again next time */ }
    }

    /** null when this is not the app (a browser). */
    async function status(): Promise<PushStatus | null> {
        const result = await nativeCall<PushStatus>('Push.Status')
        if (!result) return null
        permission.value = result.permission
        return result
    }

    /** Names the channel Android's notification settings show for this app. Call once at start; safe to skip. */
    async function setChannel(name: string, description = ''): Promise<void> {
        await nativeCall('Push.SetChannel', { name, description })
    }

    /**
     * Registers the phone's token with your API when it is new or has changed. Needs the notification permission, so
     * it does nothing (false) until the person has allowed it. Call after sign-in and whenever the app comes back.
     */
    async function sync(): Promise<boolean> {
        const current = await status()
        if (!current || current.permission !== 'granted') return false
        let token = current.token
        if (!token) token = (await nativeCall<{ token: string }>('Push.GetToken'))?.token ?? ''
        if (!token) return false
        if (token === remembered()) return true
        try {
            await options.register(token)
        } catch {
            return false
        }
        remember(token)
        return true
    }

    /**
     * Asks for the permission when it has not been asked yet (the system dialog), waits for the answer, then registers.
     * @returns the permission after asking
     */
    async function enable(): Promise<PushPermission | null> {
        let current = await status()
        if (!current) return null
        if (current.permission === 'ask') {
            await nativeCall('Push.RequestPermission')
            // the dialog answers later: wait up to a minute for the person
            for (let i = 0; i < 60 && current.permission === 'ask'; i++) {
                await sleep(1000)
                current = (await status()) ?? current
            }
        }
        if (current.permission === 'granted') await sync()
        return current.permission
    }

    /** Stops this phone's notifications for the signed-in person: tell your API, then forget the token. Call before sign-out. */
    async function disable(remove: (token: string) => Promise<void>): Promise<void> {
        const token = remembered()
        if (token) {
            try { await remove(token) } catch { /* sign-out must not wait on the network */ }
        }
        remember('')
    }

    /** The page (a path like /orders/42) of the notification the person tapped, once; '' when none. */
    async function takeLaunchLink(): Promise<string> {
        return (await nativeCall<{ link: string }>('Push.TakeLaunchLink'))?.link ?? ''
    }

    return { permission, status, setChannel, sync, enable, disable, takeLaunchLink }
}
