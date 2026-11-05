package com.vibecoderspace.linux.profile

import com.vibecoderspace.linux.RootfsLayout

/** Shell init scripts used by the bundled profiles. */
internal object InitScripts {

    fun bash(): String = """
        # linux-android bash session
        force_color_prompt=yes
        shopt -s checkwinsize 2>/dev/null
        export SHELL=/bin/bash
        export TERM=xterm-256color
        export LANG=C.UTF-8
        if [ -r /etc/profile ]; then . /etc/profile; fi
        if [ -r "${'$'}HOME/.bashrc" ]; then . "${'$'}HOME/.bashrc"; fi
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${'$'}{LOCAL}/bin
        export PS1='\[\e[1;32m\]\u@\h\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\] \$ '
        alias ls='ls --color=auto'
        alias grep='grep --color=auto'
        alias egrep='egrep --color=auto'
        alias fgrep='fgrep --color=auto'
        alias pkg='apt'
        cd "${'$'}WKDIR" 2>/dev/null || cd "${'$'}HOME" 2>/dev/null || cd /
    """.trimIndent() + "\n"
}

/** Guest environment shared by Debian-style images. */
internal fun rootfsEnvironment(
    layout: RootfsLayout,
    shell: String,
    guestPath: String,
    locale: String,
): Map<String, String> = buildMap {
    put("HOME", "/home")
    put("PATH", "$guestPath:${layout.binDir.absolutePath}")
    put("SHELL", shell)
    put("TERM", "xterm-256color")
    put("COLORTERM", "truecolor")
    put("LANG", locale)
    put("LC_ALL", locale)
    put("TMPDIR", "/tmp")
    put("USER", "root")
    put("LOGNAME", "root")
    put("PROMPT_DIRTRIM", "2")
    put("TZ", "UTC")
}
