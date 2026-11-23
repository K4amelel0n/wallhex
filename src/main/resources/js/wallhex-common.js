(() => {

    if (document._wallhex) return;

    const locatorsAttributesCandidates = ["id", "data-testid", "name", "aria-label", "href"];
    const hasAnyDigit = /\d/;

    const normalizeText = (text) => (text ?? "").replace(/\s+/g, " ").trim().slice(0, 255);

    const hasStableId = (element) => {
        const id = element.id;
        if (!id.trim()) return false;
        return !hasAnyDigit.test(id);
    }

    const isVisible = (element) => element.checkVisibility();

    const isEnabled = (element) => !element.disabled;

    const cssPath = (element) => {

        const path = [];
        let current = element;

        while (current && current !== document.body && !hasStableId(current)) {
            const tag = current.tagName.toLowerCase();
            const parent = current.parentElement;
            if (parent) {
                const children = Array.from(parent.children).filter(sibling => sibling.tagName.toLowerCase() === tag);
                const myIndex = children.findIndex(child => child === current);
                path.push(children.length > 1 ? `${tag}:nth-of-type(${myIndex + 1})` : tag);
                current = parent;
            } else {
                path.push(tag)
                current = null
            }
        }

        if (current && current !== document.body) {
            path.push(`#${CSS.escape(current.id)}`)
        }

        return path.reverse().join(" > ");
    }

    const chooseLocator = (element) => {

        for (const attribute of locatorsAttributesCandidates) {
            const value = element.getAttribute(attribute);
            if (value) {
                if (attribute === "id" && !hasStableId(element)) continue;

                const candidate = `${element.tagName.toLowerCase()}[${attribute}="${CSS.escape(value)}"]`;
                if (document.querySelectorAll(candidate).length === 1) return candidate;
            }
        }
        const path = cssPath(element);

        return document.querySelectorAll(path).length === 1 ? path : null;
    }

    document._wallhex = {
        normalizeText,
        hasStableId,
        isVisible,
        isEnabled,
        cssPath,
        chooseLocator,
    };
})();
