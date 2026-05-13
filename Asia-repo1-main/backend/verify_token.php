<?php
header("Content-Type: application/json");
require_once 'db_config.php';

$email = isset($_POST['email']) ? trim($_POST['email']) : null;
$token = isset($_POST['token']) ? trim($_POST['token']) : null;

if ($email && $token) {
    try {
        // Check both tables using UNION - using string cast to be safe
        $stmt = $conn->prepare("
            SELECT 'users' as source FROM users WHERE email = ? AND CAST(reset_token AS CHAR) = ?
            UNION
            SELECT 'residents' as source FROM residents WHERE email = ? AND CAST(reset_token AS CHAR) = ?
        ");
        $stmt->execute([$email, $token, $email, $token]);
        $result = $stmt->fetch();

        if ($result) {
            echo json_encode(["success" => true, "message" => "Token verified successfully", "source" => $result['source']]);
        } else {
            echo json_encode(["success" => false, "message" => "Invalid token. Please check your email and try again."]);
        }
    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Email and token are required"]);
}
?>
