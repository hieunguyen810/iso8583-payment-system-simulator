import ws from 'k6/ws';
import { check } from 'k6';
import { sleep } from 'k6';

export const options = {
  // Set a longer duration for stable metrics
  vus: 10,
  duration: '1m', // Increased to 1 minute
};

export default function () {
  const url = 'ws://localhost:8583';

  // Connect to the WebSocket
  const response = ws.connect(url, function (socket) {
    socket.on('open', () => {
      console.log('VU connected!');
      socket.send(JSON.stringify({ event: 'hello', message: 'from k6' }));

      // Set up a short periodic ping for the duration of the connection
      socket.setInterval(function () {
        socket.send(JSON.stringify({ event: 'ping', timestamp: Date.now() }));
      }, 1000); // Ping every 1 second
    });

    socket.on('message', (data) => {
      console.log(`VU received message: ${data}`);
      check(data, {
        'message contains expected string': (d) => d.includes('ACK'),
      });
    });

    socket.on('error', (e) => {
      if (e.error() != 'websocket: close sent') {
        console.log(`An unexpected error occurred: ${e.error()}`);
      }
    });

    // 💡 CRITICAL CHANGE: Close the socket after 5 seconds
    // This allows the iteration to complete quickly, letting the VU start the next iteration.
    socket.setTimeout(function () {
      socket.close();
    }, 5000); // Close after 5 seconds
  });

  // Check that the initial connection was successful (status code 101)
  check(response, { 'WebSocket connection successful': (r) => r && r.status === 101 });

  // Add a final sleep if needed before the VU iteration completes
  sleep(1); 
}