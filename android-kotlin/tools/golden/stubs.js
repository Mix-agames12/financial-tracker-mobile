// Módulos de React Native/Expo sustituidos por stubs en memoria para poder ejecutar la lógica TypeScript
// original (src/db, src/utils) con Node y obtener vectores dorados.
'use strict';

const kv = new Map();

const asyncStorage = {
  __esModule: true,
  default: {
    getItem: async (key) => (kv.has(key) ? kv.get(key) : null),
    setItem: async (key, value) => { kv.set(key, String(value)); },
    removeItem: async (key) => { kv.delete(key); },
    clear: async () => { kv.clear(); },
  },
};

// Lo que la app "escribiría"/"compartiría": se captura para compararlo con la versión Kotlin.
const captured = { files: [], html: null, shared: [] };

const uriOf = (part) => (typeof part === 'string' ? part : part.uri);

class File {
  constructor(...parts) {
    this.uri = parts.map(uriOf).join('/');
  }
  write(content) { captured.files.push({ uri: this.uri, content }); }
  get exists() { return false; }
  delete() {}
  move() {}
}

const fileSystem = { File, Paths: { document: { uri: 'document' }, cache: { uri: 'cache' } } };
const sharing = {
  isAvailableAsync: async () => true,
  shareAsync: async (uri, options) => { captured.shared.push({ uri, options }); },
};
const print = {
  printToFileAsync: async ({ html }) => { captured.html = html; return { uri: 'print/tmp.pdf' }; },
};
const localization = {
  getLocales: () => [{ languageTag: 'es-EC', currencyCode: 'USD', regionCode: 'EC' }],
};

module.exports = {
  kv,
  captured,
  modules: {
    '@react-native-async-storage/async-storage': asyncStorage,
    'expo-file-system': fileSystem,
    'expo-sharing': sharing,
    'expo-print': print,
    'expo-localization': localization,
  },
};
