<?php
header("Content-Type: application/json");
require_once 'db_config.php';

try {
    // Fetch Residents (Confirmed accounts in residents table)
<<<<<<< HEAD
    $resQuery = "SELECT resident_id as user_id, username, name, email, 'resident' as role, phone, purok, complete_address, status, created_at FROM residents";
=======
    $resQuery = "SELECT resident_id as user_id, username, name, email, 'resident' as role, phone, purok, complete_address, created_at, is_archived FROM residents";
>>>>>>> 117a85521b466e4f823d227f35cd645078d64a09
    $resStmt = $conn->prepare($resQuery);
    $resStmt->execute();
    $residents = $resStmt->fetchAll(PDO::FETCH_ASSOC);

    // Fetch Drivers and Admins
<<<<<<< HEAD
    $userQuery = "SELECT user_id, username, name, email, role, phone, license_number, preferred_truck, status, created_at FROM users";
=======
    $userQuery = "SELECT user_id, username, name, email, role, phone, license_number, preferred_truck, created_at, is_archived FROM users";
>>>>>>> 117a85521b466e4f823d227f35cd645078d64a09
    $userStmt = $conn->prepare($userQuery);
    $userStmt->execute();
    $users = $userStmt->fetchAll(PDO::FETCH_ASSOC);

    // Fetch Registration Requests (Pending, Approved, Declined)
    $reqQuery = "SELECT request_id as user_id, username, name, email, role, phone, purok, complete_address, license_number, preferred_truck, status, created_at FROM registration_requests ORDER BY created_at DESC";
    $reqStmt = $conn->prepare($reqQuery);
    $reqStmt->execute();
    $requests = $reqStmt->fetchAll(PDO::FETCH_ASSOC);

    echo json_encode([
        "success" => true,
        "residents" => $residents,
        "users" => $users,
        "requests" => $requests
    ]);

} catch (PDOException $e) {
    echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
}
?>
