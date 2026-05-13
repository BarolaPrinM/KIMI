<?php
header("Content-Type: application/json");
require_once 'db_config.php';

$email = isset($_POST['email']) ? trim($_POST['email']) : null;
$password = isset($_POST['password']) ? $_POST['password'] : null;

if ($email && $password) {
    try {
        $hashed_password = password_hash($password, PASSWORD_BCRYPT);

        $stmtRes = $conn->prepare("UPDATE residents SET password_hash = ?, reset_token = NULL WHERE email = ?");
        $stmtRes->execute([$hashed_password, $email]);
        $rows1 = $stmtRes->rowCount();

        $stmtUser = $conn->prepare("UPDATE users SET password_hash = ?, reset_token = NULL WHERE email = ?");
        $stmtUser->execute([$hashed_password, $email]);
        $rows2 = $stmtUser->rowCount();

        if ($rows1 > 0 || $rows2 > 0) {
            echo json_encode(["success" => true, "message" => "Password updated successfully"]);
        } else {
            echo json_encode(["success" => false, "message" => "Account not found or password is the same"]);
        }
    } catch (PDOException $e) {
        echo json_encode(["success" => false, "message" => "Database Error: " . $e->getMessage()]);
    }
} else {
    echo json_encode(["success" => false, "message" => "Email and password are required"]);
}
?>
