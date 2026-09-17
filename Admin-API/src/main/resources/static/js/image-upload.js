const CSRF_HEADER = 'X-CSRF-TOKEN';

const initImageUpload = () => {
    const input = document.getElementById('image-upload');
    if (!input || typeof FilePond === 'undefined') {
        return;
    }

    const form = input.closest('form');
    const target = form.elements.image;
    const current = document.getElementById('image-current');
    const csrf = form.elements._csrf ? form.elements._csrf.value : '';

    FilePond.registerPlugin(
        FilePondPluginImagePreview,
        FilePondPluginFileValidateType,
        FilePondPluginFileValidateSize,
    );

    const pond = FilePond.create(input, {
        credits: false,
        name: 'file',
        labelIdle: input.dataset.label,
        acceptedFileTypes: ['image/jpeg', 'image/png', 'image/webp', 'image/gif'],
        labelFileTypeNotAllowed: input.dataset.unsupported,
        maxFileSize: input.dataset.maxSize,
        labelMaxFileSizeExceeded: input.dataset.tooLarge,
        labelMaxFileSize: '',
        server: {
            process: {
                url: input.dataset.url,
                method: 'POST',
                headers: {[CSRF_HEADER]: csrf},
                onload: (key) => {
                    target.value = key;
                    current?.classList.add('is-hidden');
                    return key;
                },
                onerror: (response) => response,
            },
            revert: {
                url: input.dataset.url,
                method: 'DELETE',
                headers: {[CSRF_HEADER]: csrf, 'Content-Type': 'text/plain'},
            },
        },
    });

    pond.on('removefile', () => {
        target.value = '';
        current?.classList.add('is-hidden');
    });
};

document.addEventListener('DOMContentLoaded', initImageUpload);
