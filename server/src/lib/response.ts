export function jsonResponse(data: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, X-Device-Id, X-App-Version',
      ...headers,
    },
  });
}

export function errorResponse(code: string, message: string, status = 400): Response {
  return jsonResponse(
    {
      success: false,
      error: {
        code,
        message,
      },
    },
    status
  );
}
