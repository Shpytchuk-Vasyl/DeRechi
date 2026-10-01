const PLACE_FIELDS = ['place_id', 'name', 'formatted_address', 'geometry', 'address_components'];

const NAME_LIMIT = 100;

const setValue = (form, name, value) => {
    const input = form.elements[name];
    if (input) {
        input.value = value;
    }
};

const countryOf = (place) => {
    const component = (place.address_components || []).find((item) => item.types.includes('country'));
    return component && component.short_name ? component.short_name.toUpperCase() : null;
};

const selectCountry = (form, code) => {
    const select = form.elements['countryCode'];
    if (!select || !code) {
        return;
    }
    if (Array.from(select.options).some((option) => option.value === code)) {
        select.value = code;
    }
};

const restrictedCountries = (input) => {
    const source = input.dataset.countries || input.dataset.region || '';
    return source.toLowerCase().split(',').map((code) => code.trim()).filter(Boolean);
};

const showChosen = (hint, place) => {
    if (!hint) {
        return;
    }

    hint.textContent = place.formatted_address || place.name || '';
    hint.classList.remove('is-hidden');
};

const revealManualFields = () => {
    document.getElementById('place-search')?.closest('.field')?.classList.add('is-hidden');
    document.getElementById('place-manual')?.setAttribute('open', '');
};

function initPlaceAutocomplete() {
    const input = document.getElementById('place-search');
    if (!input) {
        return;
    }

    const form = input.closest('form');
    const hint = document.getElementById('place-chosen');

    input.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
        }
    });

    const autocomplete = new google.maps.places.Autocomplete(input, {
        fields: PLACE_FIELDS,
        componentRestrictions: {country: restrictedCountries(input)},
    });

    autocomplete.addListener('place_changed', () => {
        const place = autocomplete.getPlace();

        if (!place.geometry || !place.geometry.location) {
            return;
        }

        setValue(form, 'placeId', place.place_id);
        setValue(form, 'placeName', (place.name || place.formatted_address || '').slice(0, NAME_LIMIT));
        setValue(form, 'lat', place.geometry.location.lat());
        setValue(form, 'lon', place.geometry.location.lng());
        selectCountry(form, countryOf(place));
        showChosen(hint, place);
    });
}

window.gm_authFailure = revealManualFields;
