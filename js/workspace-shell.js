/* Runs synchronously after the static shell, before page content is parsed. */
updateNavbarUser();
(() => {
    const navigation = document.getElementById('workspaceNavigation');
    if (!navigation) return;
    function rememberScroll() {
        const user = getCurrentUser();
        if (user) {
            try { sessionStorage.setItem('workspace-nav-scroll:' + user.username, String(navigation.scrollTop)); } catch {}
        }
    }
    navigation.addEventListener('scroll', rememberScroll, { passive: true });
    navigation.addEventListener('click', event => {
        const link = event.target.closest('a');
        if (!link || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
        rememberScroll();
        if (link.pathname === window.location.pathname && !link.hash && !link.search) event.preventDefault();
    });
    window.addEventListener('pagehide', rememberScroll);
})();
