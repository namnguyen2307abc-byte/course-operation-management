const BASE = "http://localhost:8080";

async function login(username, password) {
  const res = await fetch(`${BASE}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password })
  });
  const data = await res.json();
  return data.token;
}

function checkMojibake(name, obj) {
  const str = JSON.stringify(obj);
  // Mojibake patterns like "á»", "Ä", "Ã¡", "Æ¡", etc.
  const mojibakeRegex = /(á»|Ä‘|Ä\u0090|Ã¡|Ã|Æ¡|Æ°|â€|ï¿½)/g;
  const matches = str.match(mojibakeRegex);
  if (matches && matches.length > 0) {
    console.log(`[FAIL] ${name} has potential mojibake:`, matches.slice(0, 5));
    console.log(str.slice(0, 300));
    return false;
  }
  console.log(`[PASS] ${name} clean UTF-8. Count: ${Array.isArray(obj) ? obj.length : 1}. Sample:`, str.slice(0, 120));
  return true;
}

async function test() {
  console.log("=== Testing Authentication & Endpoints ===");
  const pToken = await login("parent_test_01", "123456");
  const sToken = await login("staff_lan", "123456");
  const aToken = await login("admin", "123456");

  const endpoints = [
    { name: "Parent Children", url: "/api/course-enrollment/children", token: pToken },
    { name: "Course List", url: "/api/course-enrollment/courses", token: pToken },
    { name: "Parent Requests", url: "/api/course-enrollment/requests/my", token: pToken },
    { name: "Placement Recom", url: "/api/course-enrollment/children/12/class-recommendations", token: pToken },
    { name: "Staff Pending", url: "/api/course-enrollment/staff/requests", token: sToken },
    { name: "Staff Open Classes", url: "/api/course-enrollment/courses/1/classes", token: sToken },
    { name: "Tuition Search", url: "/api/tuition-payment/search", token: sToken },
    { name: "Tuition History", url: "/api/tuition-payment/invoices", token: sToken },
    { name: "Absence Requests", url: "/api/attendance-makeup/absence-requests", token: aToken },
    { name: "Absence Makeup Classes", url: "/api/attendance-makeup/makeup-classes?studentId=1&absenceRequestId=1", token: aToken },
    { name: "Branches", url: "/api/branches", token: sToken }
  ];

  for (const ep of endpoints) {
    try {
      const res = await fetch(`${BASE}${ep.url}`, {
        headers: { "Authorization": `Bearer ${ep.token}` }
      });
      const data = await res.json();
      checkMojibake(ep.name, data);
    } catch (err) {
      console.error(`[ERROR] ${ep.name}:`, err.message);
    }
  }
}

test();
