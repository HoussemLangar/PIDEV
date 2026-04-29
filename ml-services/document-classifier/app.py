"""Document Type Classifier Service.

This implementation uses only the Python standard library so it can run on the
target system without pip or third-party packages.
"""

import json
import logging
import os
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

DOCUMENT_TYPES = [
    "medical analysis",
    "lab results",
    "imaging scan",
    "prescription",
    "medical report",
    "appointment notes",
    "other",
]

KEYWORDS = {
    "medical analysis": ["analysis", "exam", "assessment", "screening"],
    "lab results": ["lab", "laboratory", "blood", "urine", "test", "result", "results"],
    "imaging scan": ["xray", "x-ray", "ct", "mri", "scan", "imaging", "ultrasound", "radiology"],
    "prescription": ["prescription", "rx", "medication", "meds", "tablet", "dose", "dosage", "drug"],
    "medical report": ["report", "summary", "medical report", "discharge", "consultation", "note"],
    "appointment notes": ["appointment", "visit", "follow up", "follow-up", "notes", "agenda"],
}


def normalize_text(value):
    return re.sub(r"[^a-z0-9\s\-]+", " ", (value or "").lower()).strip()


def score_document(text):
    normalized = normalize_text(text)
    scores = {document_type: 0.05 for document_type in DOCUMENT_TYPES}
    for document_type, keywords in KEYWORDS.items():
        for keyword in keywords:
            if keyword in normalized:
                scores[document_type] += 0.18

    if not normalized:
        scores["other"] = 1.0

    top_type = max(scores, key=scores.get)
    top_score = min(scores[top_type], 0.99)
    ordered = sorted(scores.items(), key=lambda item: item[1], reverse=True)
    candidates = [{"type": document_type, "score": round(min(score, 0.99), 4)} for document_type, score in ordered]
    return top_type, round(top_score, 4), candidates


def read_json_body(handler):
    content_length = int(handler.headers.get("Content-Length", 0))
    raw_body = handler.rfile.read(content_length) if content_length > 0 else b"{}"
    if not raw_body:
        return {}
    return json.loads(raw_body.decode("utf-8"))


class DocumentClassifierHandler(BaseHTTPRequestHandler):
    def _send_json(self, status_code, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"status": "ok", "classifier_ready": True})
            return
        self._send_json(404, {"error": "not found"})

    def do_POST(self):
        try:
            data = read_json_body(self)
        except json.JSONDecodeError:
            self._send_json(400, {"error": "invalid JSON"})
            return

        if self.path == "/classify":
            filename = data.get("filename", "")
            description = data.get("description", "")
            combined = f"{filename} {description}".strip()
            if not combined:
                self._send_json(400, {"error": "filename or description required"})
                return

            predicted_type, confidence, candidates = score_document(combined)
            self._send_json(200, {
                "predicted_type": predicted_type,
                "confidence": confidence,
                "candidates": candidates,
            })
            return

        if self.path == "/batch-classify":
            documents = data.get("documents", [])
            if not documents:
                self._send_json(400, {"error": "documents array required"})
                return

            results = []
            for document in documents:
                filename = document.get("filename", "")
                description = document.get("description", "")
                combined = f"{filename} {description}".strip()
                if not combined:
                    results.append({"filename": document.get("filename"), "error": "filename or description required"})
                    continue
                predicted_type, confidence, _ = score_document(combined)
                results.append({
                    "filename": document.get("filename"),
                    "predicted_type": predicted_type,
                    "confidence": confidence,
                })

            self._send_json(200, {"results": results})
            return

        self._send_json(404, {"error": "not found"})

    def log_message(self, format, *args):
        logger.info("%s - %s", self.address_string(), format % args)


if __name__ == "__main__":
    port = int(os.getenv("ML_PORT", 5001))
    server = ThreadingHTTPServer(("0.0.0.0", port), DocumentClassifierHandler)
    logger.info("Document Classifier Service listening on http://localhost:%s", port)
    server.serve_forever()
