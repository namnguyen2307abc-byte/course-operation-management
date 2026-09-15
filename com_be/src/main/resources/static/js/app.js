document.addEventListener("DOMContentLoaded", () => {
    const refreshIcons = () => window.lucide?.createIcons();
    const menu = document.querySelector(".menu-toggle");
    const navigation = document.getElementById("mainNavbar");
    menu?.addEventListener("click", () => {
        const open = navigation.classList.toggle("is-open");
        menu.setAttribute("aria-expanded", String(open));
        menu.setAttribute("aria-label", open ? "Close navigation" : "Open navigation");
    });
    document.addEventListener("keydown", (event) => {
        if (event.key === "Escape" && navigation?.classList.contains("is-open")) {
            navigation.classList.remove("is-open");
            menu.setAttribute("aria-expanded", "false");
            menu.setAttribute("aria-label", "Open navigation");
            menu.focus();
        }
    });
    document.querySelectorAll("[data-nav]").forEach((link) => {
        const path = link.dataset.nav;
        if (location.pathname === path || (path !== "/" && location.pathname.startsWith(path + "/"))) {
            link.setAttribute("aria-current", "page");
        }
    });
    document.querySelectorAll('input[type="password"]').forEach((input) => {
        const wrapper = document.createElement("div");
        wrapper.className = "password-wrap";
        input.before(wrapper);
        wrapper.append(input);
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "icon-button password-toggle";
        toggle.setAttribute("aria-label", "Show password");
        toggle.title = "Show password";
        toggle.setAttribute("aria-controls", input.id);
        toggle.setAttribute("aria-pressed", "false");
        toggle.innerHTML = '<i data-lucide="eye" aria-hidden="true"></i>';
        wrapper.append(toggle);
        toggle.addEventListener("click", () => {
            const show = input.type === "password";
            input.type = show ? "text" : "password";
            toggle.setAttribute("aria-pressed", String(show));
            toggle.setAttribute("aria-label", show ? "Hide password" : "Show password");
            toggle.title = show ? "Hide password" : "Show password";
            toggle.innerHTML = '<i data-lucide="' + (show ? "eye-off" : "eye") + '" aria-hidden="true"></i>';
            refreshIcons();
        });
    });
    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (!window.confirm(form.dataset.confirm)) event.preventDefault();
        });
    });
    refreshIcons();
});
