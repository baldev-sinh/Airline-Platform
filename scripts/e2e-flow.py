"""
End-to-end check of the customer flow against a running local stack (scripts/start-local.ps1, payment stub).

  python scripts/e2e-flow.py [flightInstanceId]

Every step talks to the API gateway (http://localhost:5000) exactly like the web app, and checks e-mails in
Mailpit (http://localhost:8025). Prints PASS/FAIL per step and stops at the first failure.
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

GATEWAY = "http://localhost:5000"
MAILPIT = "http://localhost:8025"
INSTANCE_ID = int(sys.argv[1]) if len(sys.argv) > 1 else 32


def call(method, path, body=None, token=None, base=GATEWAY, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "e2e-flow")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None), dict(r.headers)
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            payload = json.loads(raw) if raw else None
        except ValueError:
            payload = raw.decode(errors="replace")
        return e.code, payload, dict(e.headers)


def step(name, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + name + (f"  ({detail})" if detail else ""))
    if not ok:
        sys.exit(1)


def wait(what, check, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        value = check()
        if value:
            return value
        time.sleep(1)
    step(what, False, f"not within {timeout}s")


def mails_to(address):
    _, result, _ = call("GET", "/api/v1/search?" + urllib.parse.urlencode({"query": f"to:{address}"}), base=MAILPIT)
    return [m["Subject"] for m in (result or {}).get("messages", [])]


email = f"e2e-{uuid.uuid4().hex[:8]}@example.com"
password = "long-enough-password"
print(f"Customer {email}, flight instance {INSTANCE_ID}\n")

# 1. Sign-up: access + refresh token, welcome e-mail via user.registered -> notification-service
status, auth, _ = call("POST", "/auth/signup", {"fullName": "Asha Rao", "email": email, "password": password,
                                                "phone": "+919800000000"})
step("sign-up returns access and refresh tokens", status == 201 and auth.get("jwt") and auth.get("refreshToken"),
     f"HTTP {status}")
token, refresh = auth["jwt"], auth["refreshToken"]
wait("welcome e-mail", lambda: any("Welcome" in s for s in mails_to(email)))
step("welcome e-mail delivered (outbox -> Kafka -> notification-service -> Mailpit)", True)

# 2. Flight, fare and two free seats, as the checkout page loads them
status, instance, _ = call("GET", f"/api/flight-instances/{INSTANCE_ID}", token=token)
step("flight instance loads", status == 200, f"{instance.get('flightNumber')} on {instance.get('departureDateTime')}"
     if status == 200 else f"HTTP {status}")
status, cabins, _ = call("GET", f"/api/flight-instance-cabins/flight-instance/{INSTANCE_ID}?size=20", token=token)
cabin = next((c for c in cabins["content"] if c.get("cabinClassType") == "ECONOMY"), cabins["content"][0])
seats = [s["id"] for s in cabin["seats"] if s.get("status") == "AVAILABLE"][:2]
step("two available seats", len(seats) == 2, f"cabin {cabin['cabinClassType']}, seats {seats}")
status, fares, _ = call("GET", f"/api/fares/flight/{instance['flightId']}/cabin-class/{cabin['cabinClass']['id']}", token=token)
step("fare available", status == 200 and fares, f"{len(fares) if isinstance(fares, list) else fares} fares")


def refundable(f):
    code, rules, _ = call("GET", f"/api/fare-rules/fare/{f['id']}", token=token)
    return code == 404 or (code == 200 and rules.get("isRefundable"))  # no rules = full refund


fare = next((f for f in fares if refundable(f)), fares[0])
expect_refund = refundable(fare)
print(f"     using fare {fare['id']} ({fare.get('name')}), refundable: {bool(expect_refund)}")

# 3. Checkout -> payment link (stub), browser verify -> payment.completed -> CONFIRMED + tickets
passengers = [{"firstName": n, "lastName": "Rao", "email": email, "phone": "+919800000000", "dateOfBirth": "1990-01-01",
               "gender": "FEMALE", "seatInstanceId": s, "nationality": "IN"} for n, s in zip(["Asha", "Ravi"], seats)]
status, checkout, _ = call("POST", "/api/bookings", {
    "flightId": instance["flightId"], "flightInstanceId": INSTANCE_ID, "cabinClass": cabin["cabinClassType"],
    "fareId": fare["id"], "passengers": passengers, "contactInfo": {"email": email, "phone": "+919800000000"},
    "ancillaryIds": [], "mealIds": []}, token=token)
step("booking created with a checkout link", status in (200, 201) and checkout.get("checkoutUrl"),
     f"HTTP {status} {checkout if status >= 400 else ''}")
link = urllib.parse.parse_qs(urllib.parse.urlparse(checkout["checkoutUrl"]).query)
booking_id = int(urllib.parse.urlparse(checkout["checkoutUrl"]).path.rsplit("/", 1)[-1])
status, payment, _ = call("POST", "/api/payments/verify", {"razorpayPaymentId": link["razorpay_payment_id"][0],
                                                           "razorpayPaymentLinkId": link["razorpay_payment_link_id"][0]},
                          token=token)
step("payment verified", status == 200 and payment.get("status") == "SUCCESS", f"HTTP {status}")
booking = wait("booking confirmed", lambda: (lambda b: b if b.get("status") == "CONFIRMED" else None)(
    call("GET", f"/api/bookings/{booking_id}", token=token)[1]))
step("booking CONFIRMED with e-tickets", len(booking.get("tickets") or []) == 2,
     f"ref {booking['bookingReference']}, total {booking['totalAmount']} {booking['currency']}")
wait("confirmation e-mail", lambda: any("Booking confirmed" in s for s in mails_to(email)))
step("confirmation e-mail delivered", True)

# 4. Cancel the ticketed booking -> refund.requested -> payment-service refund -> payment.refunded -> REFUNDED
status, cancelled, _ = call("PATCH", f"/api/bookings/{booking_id}/cancel", token=token)
step("ticketed booking cancelled", status == 200 and cancelled.get("status") == "CANCELLED",
     f"refund {cancelled.get('refundStatus')} {cancelled.get('refundAmount')} - {cancelled.get('cancellationReason')}")
if expect_refund and cancelled.get("refundStatus") == "PENDING":
    refunded = wait("refund processed", lambda: (lambda b: b if b.get("refundStatus") == "REFUNDED" else None)(
        call("GET", f"/api/bookings/{booking_id}", token=token)[1]))
    step("refund REFUNDED, tickets REFUNDED",
         all(t["status"] == "REFUNDED" for t in refunded["tickets"]), f"{refunded['refundAmount']} on {refunded['refundedAt']}")
    wait("cancellation and refund e-mails", lambda: (lambda subjects: any("cancelled" in s for s in subjects)
                                                     and any("Refund processed" in s for s in subjects))(mails_to(email)))
    step("cancellation and refund e-mails delivered", True)
    expected_unread = 4
else:
    step("no refund, as the fare rules say", cancelled.get("refundStatus") in ("NONE", None))
    wait("cancellation e-mail", lambda: any("cancelled" in s for s in mails_to(email)))
    step("cancellation e-mail delivered", True)
    expected_unread = 3
status, unread, _ = call("GET", "/api/notifications/unread-count", token=token)
step("in-app inbox has the notifications", status == 200 and unread["unread"] >= expected_unread, f"{unread} unread")

# 5. Tokens: rotation, reuse detection, logout
status, rotated, _ = call("POST", "/auth/refresh", {"refreshToken": refresh})
step("refresh rotates the token pair", status == 200 and rotated["refreshToken"] != refresh)
status, _, _ = call("POST", "/auth/refresh", {"refreshToken": refresh})
step("reusing a spent refresh token is rejected", status == 401)
status, _, _ = call("GET", "/api/users/profile", token=rotated["jwt"])
step("...and revokes the whole session (new access token rejected)", status == 401, f"HTTP {status}")
time.sleep(1.1)
status, login, _ = call("POST", "/auth/login", {"email": email, "password": password})
step("sign in again", status == 200)
status, _, _ = call("POST", "/auth/logout", {"refreshToken": login["refreshToken"]}, token=login["jwt"])
status2, _, _ = call("GET", "/api/users/profile", token=login["jwt"])
step("logout revokes the access token immediately", status == 204 and status2 == 401)
status, _, _ = call("GET", "/api/users/profile", headers={"X-User-Id": "1", "X-User-Roles": "ROLE_SYSTEM_ADMIN"})
step("forged identity headers without a token are rejected", status == 401)

# 6. Rate limit on login (10 per minute per client)
codes = [call("POST", "/auth/login", {"email": email, "password": "wrong-password"})[0] for _ in range(12)]
step("login rate limit answers 429", 429 in codes, f"{codes.count(401)}x401 then {codes.count(429)}x429")

print("\nAll steps passed. Open Mailpit to read the e-mails: " + MAILPIT)
