window.onload = function() {
  window.ui = SwaggerUIBundle({
    urls: [
      { name: "Scaladex API v1", url: "/api/v1/open-api.json" },
      { name: "Scaladex API v0", url: "/api/open-api.json" }
    ],
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [
      SwaggerUIBundle.presets.apis,
      SwaggerUIStandalonePreset
    ],
    plugins: [
      SwaggerUIBundle.plugins.DownloadUrl
    ],
    layout: "StandaloneLayout"
  });
};
