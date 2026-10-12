# NetSend LAN Messenger

Ứng dụng desktop JavaFX nhắn tin ngang hàng qua UDP cho mạng nội bộ. Mỗi instance vừa gửi vừa lắng nghe; không cần database hay server trung tâm. Sender name là thông tin do peer tự khai báo, không phải danh tính đã xác thực.

## Chức năng

- UDP unicast tới IP/cổng listener cấu hình.
- UDP multicast theo group IPv4, có join/leave theo cấu hình group.
- UDP broadcast tới broadcast address cấu hình.
- Discovery request/response và heartbeat broadcast; thiết bị Online là ước tính khi nhận heartbeat trong 45 giây.
- Lịch sử JSON cục bộ, tìm kiếm, lọc chiều gửi/nhận, loại đích và ngày; xóa có xác nhận, sao chép tin nhắn.
- Cấu hình tên, IPv4 interface, listen port, broadcast address, thông báo và multicast groups.
- Giao diện JavaFX không thực hiện socket I/O trên JavaFX Application Thread; lỗi mạng được ghi ở Logs.

## Kiến trúc

```text
JavaFX App
  └─ MessengerService ── UdpNetworkService (UDP listener + multicast sockets)
       ├─ PacketCodec (JSON, version 1, max 8192 bytes)
       ├─ DuplicateMessageGuard (message ID, 5 phút)
       ├─ JSON local storage (~/.netsend-lan/)
       └─ Discovery/heartbeat (broadcast trên listen port)
```

Protocol có `protocolVersion`, `messageId`, `messageType`, `senderName`, timestamp, `targetType`, `targetId` và payload. Gói tin sai định dạng, quá 8192 byte, loại/phiên bản không được hỗ trợ hoặc payload vượt 1000 ký tự bị từ chối. UDP không mã hóa, không xác thực sender, không bảo đảm giao hàng; thành công khi gửi chỉ xác nhận socket cục bộ đã gửi datagram.

## Yêu cầu và chạy ứng dụng

- JDK 17 trở lên.
- Maven 3.8 trở lên.
- Máy desktop có hỗ trợ JavaFX native platform.

```powershell
mvn clean test
mvn javafx:run
```

Build tạo project `com.netsend:netsend-lan-messenger`. Maven JavaFX plugin chạy `com.netsend.App`.

## Cấu hình mạng

Mở **Settings**:

- Display name: tên hiển thị; không phải xác thực.
- Network interface IPv4: chọn địa chỉ IPv4 cục bộ đang hoạt động; để trống để hệ điều hành chọn. Khi chọn, địa chỉ này dùng cho multicast join và bind nguồn gửi.
- UDP listen port: cổng unicast/broadcast/discovery nhận; hai instance trên cùng máy cần cổng khác nhau.
- Broadcast address: nhập broadcast phù hợp subnet, ví dụ `192.168.1.255`. `255.255.255.255` là limited broadcast và thường bị giới hạn.
- Notifications: bật/tắt alert khi có tin nhắn đến.

Cấu hình và lịch sử nằm tại `%USERPROFILE%\.netsend-lan\config.json` và `%USERPROFILE%\.netsend-lan\history.json` trên Windows (tương đương `~/.netsend-lan/`). `--dataDir` chọn thư mục dữ liệu riêng; có thể truyền `--listenPort`, `--displayName`, `--broadcastAddress`, `--interfaceAddress` trong `javafx.args`.

## Thử với hai instance trên cùng máy

1. Chạy instance A bình thường bằng `mvn javafx:run`.
2. Chạy instance B với profile, port và tên riêng (PowerShell):

   ```powershell
   mvn javafx:run '-Djavafx.args=--listenPort=45455 --displayName=Peer-B --dataDir=C:\Users\<user>\.netsend-instance-b'
   ```

   Instance B có file config/history riêng, không đụng cấu hình của A.
3. Trong A, gửi Direct tới `127.0.0.1`, nhập **Cổng UDP máy nhận** là `45455`. Do hai instance cùng máy không thể cùng bind một port, nhập port riêng của peer nhận. Trên LAN, thông thường mọi peer cùng dùng port chuẩn đã thống nhất.
4. Multicast: tạo cùng group address (mặc định `239.10.10.10`) và port ở các peers; join group được thực hiện khi listener start hoặc sau khi cập nhật group. Gửi tin ở tab Groups.
5. Broadcast: cấu hình directed broadcast đúng subnet và thử ở tab LAN Broadcast. Mạng Wi-Fi client isolation, router, firewall và VPN có thể chặn broadcast.

**Lưu ý về nhiều instance cùng máy:** mỗi instance cần listen port riêng; heartbeat và discovery quảng bá listen port để danh sách thiết bị có thể mở Direct Message tới đúng port. Discovery/broadcast giữa các instance cùng máy vẫn phụ thuộc OS và việc broadcast loopback được hỗ trợ hay không.

## Thử trên LAN và Windows Firewall

1. Cho phép ứng dụng Java (`java.exe`) nhận UDP inbound tại listen port đã chọn trên **Private network**; nếu cần, tạo inbound rule UDP port tương ứng trong Windows Defender Firewall.
2. Đảm bảo các máy cùng subnet/VLAN, đúng interface, không bật client isolation; dùng directed broadcast của subnet thay vì gửi broadcast qua Internet.
3. Gửi unicast đến địa chỉ IPv4 máy nhận và listen port mà máy nhận đang lắng nghe.
4. Tạo cùng multicast group/port trên hai peer rồi gửi group message.
5. Với broadcast, cho phép UDP inbound port và directed broadcast trên thiết bị mạng.
6. Đổi interface, dừng listener, thử port đang bị chiếm và thử lại sau khi cho phép firewall.

Firewall rule mở rộng phạm vi nhận gói trong LAN; chỉ cấu hình trên mạng tin cậy, giới hạn profile Private và xóa rule sau khi thực hành nếu không còn cần.

## Lỗi thường gặp

- **Address/port không hợp lệ:** chỉ IPv4 dạng dotted-decimal; port 1–65535.
- **Port đã dùng:** chọn cổng UDP chưa được ứng dụng khác bind; alert hiển thị lỗi khởi tạo socket.
- **Không thấy peer:** kiểm tra địa chỉ broadcast, subnet, interface và firewall. Socket mở thành công không chứng minh peer truy cập được.
- **Không nhận multicast:** xác nhận đúng group trong dải 224.0.0.0/4, cùng port/interface; kiểm tra IGMP, switch/router và firewall. Không dùng địa chỉ multicast dành riêng.
- **Broadcast không hoạt động:** một số Wi-Fi/AP/router chặn broadcast; UDP không có ACK mặc định.
- **Đổi card mạng:** chọn lại IPv4 interface trong Settings rồi restart listener.

## Sơ đồ luồng gửi/nhận

```text
UI validates input
  → MessengerService builds versioned ProtocolPacket
  → PacketCodec validates and serializes JSON
  → UDP datagram to unicast / multicast / broadcast address
  → receiver validates size + JSON + version + fields + duplicate ID
  → discovery updates device heartbeat OR chat message records source IP
  → append local JSON history
  → callback marshalled with Platform.runLater to JavaFX
```

## Kiểm thử

Đã chạy trong môi trường phát triển:

- `mvn -B package` — BUILD SUCCESS; 9 tests, 0 failures (IPv4/message constraints, protocol round-trip/malformed payload, duplicate IDs, rate limiting, JSON history, UDP loopback và unicast end-to-end giữa hai MessengerService).
- Integration tests loopback gửi UDP datagram thật và xác nhận source IP; bài test service xác nhận nội dung, sender name và lịch sử đầu nhận.

Checklist kiểm thử LAN thủ công:

1. **Unicast hai máy:** gửi IP/port đích; máy nhận thấy sender, source IP thực tế, nội dung và thời gian nhận.
2. **Multicast:** hai peer join cùng address/port và nhận tin group.
3. **Không tham gia group:** peer không join group không nhận message group.
4. **Broadcast:** thử directed broadcast cùng subnet; kết quả phụ thuộc cấu hình mạng.
5. **Gửi đồng thời:** nhiều peer gửi liên tục; kiểm tra UI vẫn phản hồi và không trùng message ID.
6. **Dữ liệu sai:** thử IP/port/payload không hợp lệ, JSON sai hoặc datagram quá kích thước để xác nhận bị từ chối rõ ràng.
7. **Đóng khi đang nhận:** đóng app trong lúc listener hoạt động; xác nhận socket/thread dừng sạch.
8. **Port bị chiếm:** khởi động hai listener cùng port; instance thứ hai phải báo lỗi bind.
9. **Firewall:** chặn/mở inbound UDP và xác nhận peer discover/nhận thay đổi tương ứng.
10. **Đổi network interface:** chọn interface khác rồi restart, xác nhận gửi/nhận dùng interface được chọn.

Chưa kiểm chứng thực địa trong môi trường này: các mục 1–5, 7, 9 và 10 trên LAN thật. Loopback không chứng minh được các đặc tính đó; mục 6 và 8 chỉ được kiểm tra một phần bằng unit/integration tests, không phải toàn bộ manual scenario.

## Giới hạn

- UDP không cung cấp mã hóa, xác thực, bảo đảm thứ tự hay giao hàng.
- Status sent không phải delivery receipt; không triển khai DELIVERY_ACK.
- Sender name do peer tự khai báo; source IP chỉ là địa chỉ datagram quan sát được.
- Device online dựa trên discovery/heartbeat broadcast và timeout; không phải inventory đáng tin cậy.
- Lịch sử và nhóm chỉ lưu cục bộ; không có đồng bộ/cơ sở dữ liệu.
- Thiết bị chưa join group sẽ không nhận group message; danh sách thành viên không ép thiết bị tham gia.
- Kích thước datagram được giới hạn để tránh phân mảnh IP; không có phân mảnh tin nhắn lớn.
- Giới hạn tốc độ: tối đa 20 thao tác gửi/giây, bỏ qua inbound vượt 200 datagram/giây.
