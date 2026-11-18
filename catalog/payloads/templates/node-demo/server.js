const http = require("http");

const server = http.createServer((req, res) => {
  res.writeHead(200, {"Content-Type": "text/plain; charset=utf-8"});
  res.end("Salam, Nibras Terminal!\n");
});

server.listen(3000, "127.0.0.1", () => {
  console.log("Server: http://127.0.0.1:3000");
});
