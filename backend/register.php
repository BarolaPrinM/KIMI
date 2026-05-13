<?php
header("Content-Type: application/json");
require_once 'db_config.php';

$data = json_decode(file_get_contents("php://input"));

if (!$data) {
    echo json_encode(["success" => false, "message" => "No data received"]);
    exit;
}

if (!empty($data->username) && !empty($data->password) && !empty($data->role)) {
    try {
        $username = $data->username;
        $email = !empty($data->email) ? $data->email : "";
        $phone = !empty($data->phone) ? $data->phone : null;

        // 1. Check if ALREADY APPROVED (exists in master tables)
        $checkMaster = "SELECT 1 FROM users WHERE username = ? OR email = ?
                        UNION
                        SELECT 1 FROM residents WHERE username = ? OR email = ?";
        $stmtCheck = $conn->prepare($checkMaster);
        $stmtCheck->execute([$username, $email, $username, $email]);

        if ($stmtCheck->fetch()) {
            echo json_encode(["success" => false, "message" => "Username or Email is already registered and approved."]);
            exit;
        }

        // 2. Clear any existing PENDING or DECLINED requests for this user/email to allow re-registration
        $deleteOld = "DELETE FROM registration_requests WHERE username = ? OR email = ?";
        $stmtDel = $conn->prepare($deleteOld);
        $stmtDel->execute([$username, $email]);

        // 3. Insert the new request
        $hashed_password = password_hash($data->password, PASSWORD_BCRYPT);
        $name = !empty($data->name) ? $data->name : $username;
        $role = $data->role;
        $purok = !empty($data->purok) ? $data->purok : null;
        $address = !empty($data->complete_address) ? $data->complete_address : null;
        $license = !empty($data->license_number) ? $data->license_number : null;
        $truck = !empty($data->preferred_truck) ? $data->preferred_truck : null;

        $query = "INSERT INTO registration_requests (username, name, email, password_hash, phone, role, purok, complete_address, license_number, preferred_truck, status)
                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending')";

        $stmt = $conn->prepare($query);

        if ($stmt->execute([$username, $name, $email, $hashed_password, $phone, $role, $purok, $address, $license, $truck])) {
            echo json_encode(["success" => true, "message" => "Registration request submitted successfully. Please wait for admin approval."]);
        } else {
            echo json_encode(["success" => false, "message" => "Failed to submit registration request"]);
        }

    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Incomplete data. Required: username, password, role"]);
}
?>
