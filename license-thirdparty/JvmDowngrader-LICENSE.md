# JvmDowngrader Third-Party License Notice
## Copyright Statement
Copyright (C) 2024 William Gray <jvmdowngrader@wagyourtail.xyz>

This file is embedded within FlexLoginUI's downgraded release jar to comply with GNU LGPLv2.1 binary distribution requirements.

## Dual Licensing Terms
This software JvmDowngrader is offered under two separate licensing options, users must select one applicable to their usage scenario:
1. The GNU Lesser General Public License version 2.1 (LGPLv2.1) – Free for all non-commercial use
2. Commercial And Support License Agreement – Required for all commercial monetized usage

### Non-Commercial Usage Rule
If your project FlexLoginUI is used without any commercial monetization (no paid Minecraft servers, no paid plugin sales, no paid hosting services, no paid modpack distribution), you may use JvmDowngrader free of charge under LGPLv2.1. Voluntary donations to the author are welcomed but not mandatory license obligations.

### Commercial Usage Mandatory Rule
Any commercial, profit-generating, monetized deployment of FlexLoginUI requires purchasing the official Commercial And Support License Agreement directly from the copyright holder William Gray via email: jvmdowngrader@wagyourtail.xyz. Free LGPLv2.1 licensing is invalid for commercial scenarios.

## Author Official Legal Clarification on Shading & Bytecode Downgrade
### LGPLv2.1 License Concerns Resolution
Many developers worry that shading JvmDowngrader API classes will force the entire host project to adopt GPL instead of LGPL. The copyright holder explicitly clarifies this is not the case.

For licensing definition, the final bytecode-downgraded & shaded FlexLoginUI jar file counts as a **Combined Work**:
- It contains original independent source code of FlexLoginUI (MIT licensed)
- It embeds shaded API stub classes sourced from JvmDowngrader (LGPLv2.1 licensed)

This Combined Work definition means FlexLoginUI’s core source code retains its original MIT license, and you are not required to relicense the whole project under LGPLv2.1.

### LGPLv2.1 Section 6.a Compliance Proof for FlexLoginUI
This repository fully satisfies the LGPLv2.1 §6.a requirement specified by the author:
1. Complete open-source Maven pom.xml build scripts are publicly available in the GitHub repository.
2. Any end user can clone the full source repository and run `mvn clean package` to fully reproduce the complete workflow: download JvmDowngrader CLI → bytecode downgrade → shade embed JvmDowngrader API stubs.
3. Unmodified, unobfuscated original source code of FlexLoginUI is fully published without omission.

## Full LGPLv2.1 Text Reference
Complete text of GNU Lesser General Public License v2.1: https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
