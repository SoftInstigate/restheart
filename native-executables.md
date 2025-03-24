## Running Native Executables

RESTHeart is available as native executables for multiple platforms. These binaries offer faster startup and lower memory usage compared to the Java version.

### Download

Native executables are available in the [project releases](https://github.com/SoftInstigate/restheart/releases):

- `restheart-darwin-amd64` - For Intel-based Macs
- `restheart-darwin-arm64` - For Apple Silicon Macs (M1/M2/M3)
- `restheart-linux-amd64` - For 64-bit Linux systems
- `restheart-windows-amd64.exe` - For 64-bit Windows systems

### Platform-Specific Instructions

#### macOS (both Intel and Apple Silicon)

1. Download the appropriate file for your system:
   - Intel Mac: `restheart-darwin-amd64`
   - Apple Silicon Mac: `restheart-darwin-arm64`

2. Open Terminal and navigate to the download location:
   ```bash
   cd /path/to/download/folder
   ```

3. Make the file executable:
   ```bash
   chmod +x restheart-darwin-amd64  # or restheart-darwin-arm64
   ```

4. Run the executable:
   ```bash
   ./restheart-darwin-amd64  # or ./restheart-darwin-arm64
   ```

⚠️ **Note about macOS Security:** The first time you run the executable, macOS might show a security warning stating "Apple cannot check app for malicious software". You have two options:

- **Option 1**: Right-click (or Control+click) on the file in Finder and select "Open". When prompted, click "Open" again.

- **Option 2**: Remove the quarantine attribute using Terminal:
  ```bash
  xattr -d com.apple.quarantine restheart-darwin-amd64  # or restheart-darwin-arm64
  ```

This will allow you to run the application without security warnings.

#### Linux

1. Download the Linux executable:
   ```bash
   wget https://github.com/SoftInstigate/restheart/releases/download/latest/restheart-linux-amd64
   ```

2. Make it executable:
   ```bash
   chmod +x restheart-linux-amd64
   ```

3. Run the executable:
   ```bash
   ./restheart-linux-amd64
   ```

#### Windows

1. Download the Windows executable (`restheart-windows-amd64.exe`).

2. Open Command Prompt or PowerShell and navigate to the download location:
   ```
   cd C:\path\to\download\folder
   ```

3. Run the executable:
   ```
   restheart-windows-amd64.exe
   ```

⚠️ **Note about Windows Security:** If Windows SmartScreen blocks the execution, you can click "More info" and then "Run anyway" if you trust the source.

### Configuration

The native executables use the same [configuration approach](https://restheart.org/docs/configuration) as the Java version.

### Common Command-Line Options

The native executables support the same command-line options as the Java version:

```bash
# Start with a specific config file
./restheart-[platform] /path/to/config.yml

# Override configuration properties
./restheart-[platform] --mongo-uri=mongodb://localhost

# Get help
./restheart-[platform] --help
```

### Installing as a Service

⚠️ **Note**: these configurations are provided as examples you need to check and adapt by yourself.

#### Linux (systemd)

1. Create a systemd service file:
   ```bash
   sudo nano /etc/systemd/system/restheart.service
   ```

2. Add the following content (adjust paths as needed):
   ```
   [Unit]
   Description=RESTHeart MongoDB REST API
   After=network.target

   [Service]
   Type=simple
   User=restheart
   ExecStart=/opt/restheart/restheart-linux-amd64 /opt/restheart/etc/restheart.yml
   Restart=on-failure

   [Install]
   WantedBy=multi-user.target
   ```

3. Enable and start the service:
   ```bash
   sudo systemctl enable restheart
   sudo systemctl start restheart
   ```

#### macOS (launchd)

1. Create a launch daemon plist file:
   ```bash
   sudo nano /Library/LaunchDaemons/com.softinstigate.restheart.plist
   ```

2. Add the following content (adjust paths as needed):
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
       <key>Label</key>
       <string>com.softinstigate.restheart</string>
       <key>ProgramArguments</key>
       <array>
           <string>/opt/restheart/restheart-darwin-amd64</string>
           <string>/opt/restheart/etc/restheart.yml</string>
       </array>
       <key>RunAtLoad</key>
       <true/>
       <key>KeepAlive</key>
       <true/>
   </dict>
   </plist>
   ```

3. Load the service:
   ```bash
   sudo launchctl load /Library/LaunchDaemons/com.softinstigate.restheart.plist
   ```

#### Windows (as a service)

1. Install [NSSM (Non-Sucking Service Manager)](https://nssm.cc/download)

2. Open Command Prompt as Administrator and run:
   ```
   nssm install RESTHeart
   ```

3. In the GUI that appears:
   - Set the "Path" to the location of your executable
   - Set "Startup directory" to the folder containing the executable
   - Add any arguments (e.g., path to config file)
   - Configure other options as needed
   - Click "Install service"

4. Start the service:
   ```
   net start RESTHeart
   ```