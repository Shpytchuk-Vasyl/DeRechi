const PLACE_FIELDS = ['place_id', 'name', 'formatted_address', 'geometry'];

const NAME_LIMIT = 100;

const setValue = (form, name, value) => {
    const input = form.elements[name];
    if (input) {
        input.value = value;
    }
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
        componentRestrictions: {country: input.dataset.region.toLowerCase()},
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
        showChosen(hint, place);
    });
}

window.gm_authFailure = revealManualFields;
