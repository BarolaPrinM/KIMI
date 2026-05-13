<?php
header("Content-Type: application/json");
require_once 'db_config.php';

$email = isset($_POST['email']) ? trim($_POST['email']) : null;
$token = isset($_POST['token']) ? trim($_POST['token']) : null;

if ($email && $token) {
    try {
        // Fetch hashed token and expiry
        $stmt = $conn->prepare("
            SELECT reset_token, token_expiry, 'users' as source FROM users WHERE email = ?
            UNION
            SELECT reset_token, token_expiry, 'residents' as source FROM residents WHERE email = ?
        ");
        $stmt->execute([$email, $email]);
        $result = $stmt->fetch();

        if ($result && $result['reset_token']) {
            $now = date('Y-m-d H:i:s');

            // 1. Check if expired
            if ($now > $result['token_expiry']) {
                // Auto-delete expired token
                $source = $result['source'];
                $clear = $conn->prepare("UPDATE $source SET reset_token = NULL, token_expiry = NULL WHERE email = ?");
                $clear->execute([$email]);

                echo json_encode(["success" => false, "message" => "Code has expired. Please request a new one."]);
                exit;
            }

            // 2. Verify Hashed Token
            if (password_verify($token, $result['reset_token'])) {
                // SUCCESS: Delete token after use
                $source = $result['source'];
                $clear = $conn->prepare("UPDATE $source SET reset_token = NULL, token_expiry = NULL WHERE email = ?");
                $clear->execute([$email]);

                echo json_encode(["success" => true, "message" => "Verified successfully"]);
            } else {
                echo json_encode(["success" => false, "message" => "Invalid verification code."]);
            }
        } else {
            echo json_encode(["success" => false, "message" => "No active request found for this email."]);
        }
    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Email and token are required"]);
}
?>
