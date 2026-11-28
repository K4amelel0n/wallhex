((elements) => {

    const { chooseLocator, normalizeText, hasStableId, isVisible, isEnabled } = document._wallhex;

    return elements.filter(element => isVisible(element) && isEnabled(element)).map(element => {

            const locator = chooseLocator(element);

            return ({
                locator: locator,
                id: element.id,
                hasStableId: hasStableId(element),
                tagName: element.tagName.toLowerCase(),
                text: normalizeText(element.innerText),
                href: typeof element.href === "string" ? element.href : null,
                ariaLabel: normalizeText(element.getAttribute("aria-label")),
                title: normalizeText(element.getAttribute("title")),
                placeholder: normalizeText(element.getAttribute("placeholder")),
                value: normalizeText(element.value),
                alt: normalizeText(element.querySelector("img[alt]")?.getAttribute("alt")),
                type: element.getAttribute("type"),
            })
        }
    )
})
