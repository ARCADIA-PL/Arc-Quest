import {defineConfig} from 'vite';
import {viteSingleFile} from 'vite-plugin-singlefile';

export default defineConfig({
    base: './',
    plugins: [viteSingleFile()],
    build: {
        outDir: 'dist',
        emptyOutDir: true,
        assetsInlineLimit: 100000000,
        cssCodeSplit: false,
        modulePreload: false,
        rollupOptions: {
            input: {
                'arc-quest-editor': 'index.html'
            },
            output: {
                entryFileNames: 'assets/[name].js',
                assetFileNames: 'assets/[name].[ext]',
                inlineDynamicImports: true,
                manualChunks: undefined
            }
        }
    }
});
