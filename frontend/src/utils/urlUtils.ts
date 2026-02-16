/**
 * Constructs the full URL for a photo, handling base URL adjustments.
 * @param filename - The filename of the photo.
 * @returns The full URL or null if filename is missing.
 */
export const getPhotoUrl = (filename: string | null | undefined) => {
    if (!filename) return null;

    let baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

    if (baseUrl.endsWith('/api')) {
        baseUrl = baseUrl.slice(0, -4);
    }
    if (baseUrl.endsWith('/')) {
        baseUrl = baseUrl.slice(0, -1);
    }

    return `${baseUrl}/api/uploads/photos/${filename}`;
};